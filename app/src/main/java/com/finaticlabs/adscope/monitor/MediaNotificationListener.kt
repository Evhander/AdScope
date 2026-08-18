package com.finaticlabs.adscope.monitor

import android.app.Notification
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.finaticlabs.adscope.playback.AdReplacementService
import java.lang.ref.WeakReference

class MediaNotificationListener : NotificationListenerService() {

    private lateinit var mediaSessionManager: MediaSessionManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bindings = mutableMapOf<String, SessionBinding>()

    private val activeSessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            bindToControllers(controllers.orEmpty())
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = WeakReference(this)
        mediaSessionManager = getSystemService(MediaSessionManager::class.java)

        MediaMonitorStore.setServiceConnected(
            connected = true,
            message = "Service connected. Looking for media sessions..."
        )

        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                activeSessionsListener,
                componentName(),
                mainHandler
            )
            refreshSessions()
        } catch (securityException: SecurityException) {
            MediaMonitorStore.setServiceConnected(
                connected = false,
                message = "Android has not granted service access yet."
            )
        }
    }

    override fun onListenerDisconnected() {
        cleanupBindings()
        if (::mediaSessionManager.isInitialized) {
            runCatching {
                mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsListener)
            }
        }
        instance.clear()
        MediaMonitorStore.setServiceConnected(
            connected = false,
            message = "Service disconnected. Check notification access."
        )
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.notification.category == Notification.CATEGORY_TRANSPORT) {
            refreshSessions()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.notification.category == Notification.CATEGORY_TRANSPORT) {
            refreshSessions()
        }
    }

    private fun refreshSessions() {
        if (!::mediaSessionManager.isInitialized) return
        runCatching {
            mediaSessionManager.getActiveSessions(componentName())
        }.onSuccess(::bindToControllers)
            .onFailure {
                MediaMonitorStore.setServiceConnected(
                    connected = false,
                    message = "Cannot read media sessions: ${it.message.orEmpty()}"
                )
            }
    }

    private fun bindToControllers(controllers: List<MediaController>) {
        val incoming = controllers.associateBy(::sessionId)
        val removedIds = bindings.keys - incoming.keys

        removedIds.forEach { id ->
            bindings.remove(id)?.let { binding ->
                runCatching { binding.controller.unregisterCallback(binding.callback) }
            }
        }

        incoming.forEach { (id, controller) ->
            val existing = bindings[id]
            if (existing == null) {
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        publishController(id, controller)
                    }

                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        publishController(id, controller)
                    }

                    override fun onSessionDestroyed() {
                        bindings.remove(id)?.let { binding ->
                            runCatching { binding.controller.unregisterCallback(binding.callback) }
                        }
                        refreshSessions()
                    }
                }

                controller.registerCallback(callback, mainHandler)
                bindings[id] = SessionBinding(controller, callback)
            }
            publishController(id, bindings[id]?.controller ?: controller)
        }

        MediaMonitorStore.setActiveSessions(bindings.keys)
    }

    private fun sessionId(controller: MediaController): String =
        "${controller.packageName}:${controller.sessionToken.hashCode()}"

    private fun publishController(sessionId: String, controller: MediaController) {
        val metadata = controller.metadata
        val playback = controller.playbackState

        val title = metadata.text(
            MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
            MediaMetadata.METADATA_KEY_TITLE
        )
        val artist = metadata.text(
            MediaMetadata.METADATA_KEY_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_AUTHOR
        )
        val album = metadata.text(MediaMetadata.METADATA_KEY_ALBUM)
        val duration = metadata
            ?.getLong(MediaMetadata.METADATA_KEY_DURATION)
            ?.takeIf { it > 0L }

        val isPlaying = playback?.state == PlaybackState.STATE_PLAYING
        val actions = playback?.actions ?: 0L
        val canSeek = actions and PlaybackState.ACTION_SEEK_TO != 0L
        val canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L

        val assessment = AdHeuristics.assess(
            packageName = controller.packageName,
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            isPlaying = isPlaying,
            canSeek = canSeek,
            canSkipNext = canSkipNext
        )

        val snapshot = MediaSnapshot(
                sessionId = sessionId,
                packageName = controller.packageName,
                appName = friendlyAppName(controller.packageName),
                title = title,
                artist = artist,
                album = album,
                durationMs = duration,
                playbackState = playbackStateName(playback?.state),
                isPlaying = isPlaying,
                canSeek = canSeek,
                canSkipNext = canSkipNext,
                assessment = assessment
            )

        MediaMonitorStore.publish(snapshot)
        AdReplacementService.reportSnapshot(snapshot)
    }

    private fun cleanupBindings() {
        bindings.values.forEach { binding ->
            runCatching { binding.controller.unregisterCallback(binding.callback) }
        }
        bindings.clear()
    }

    private fun componentName() = ComponentName(this, MediaNotificationListener::class.java)

    private fun friendlyAppName(packageName: String): String = when (packageName) {
        "com.spotify.music" -> "Spotify"
        "com.google.android.apps.youtube.music" -> "YouTube Music"
        "com.google.android.youtube" -> "YouTube"
        "com.amazon.mp3" -> "Amazon Music"
        "com.apple.android.music" -> "Apple Music"
        "deezer.android.app" -> "Deezer"
        else -> packageName
    }

    private fun playbackStateName(state: Int?): String = when (state) {
        PlaybackState.STATE_PLAYING -> "Playing"
        PlaybackState.STATE_PAUSED -> "Paused"
        PlaybackState.STATE_BUFFERING -> "Buffering"
        PlaybackState.STATE_CONNECTING -> "Connecting"
        PlaybackState.STATE_FAST_FORWARDING -> "Fast forwarding"
        PlaybackState.STATE_REWINDING -> "Rewinding"
        PlaybackState.STATE_SKIPPING_TO_NEXT -> "Skipping to next"
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "Skipping to previous"
        PlaybackState.STATE_STOPPED -> "Stopped"
        PlaybackState.STATE_ERROR -> "Error"
        PlaybackState.STATE_NONE -> "No state"
        else -> "Unknown"
    }

    private fun MediaMetadata?.text(vararg keys: String): String? {
        if (this == null) return null
        return keys.firstNotNullOfOrNull { key ->
            getText(key)?.toString()?.trim()?.takeIf(String::isNotEmpty)
        }
    }

    private data class SessionBinding(
        val controller: MediaController,
        val callback: MediaController.Callback
    )

    companion object {
        private var instance = WeakReference<MediaNotificationListener>(null)

        fun requestRefresh() {
            instance.get()?.refreshSessions()
        }
    }
}
