package com.finaticlabs.adscope.playback

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

const val MIN_REPLACEMENT_TRACKS = 5

data class ReplacementTrack(
    val uri: String,
    val name: String
)

enum class ReplacementPhase {
    DISABLED,
    WAITING,
    AD_ACTIVE,
    RESTORING,
    TESTING,
    ERROR
}

data class ReplacementState(
    val tracks: List<ReplacementTrack> = emptyList(),
    val enabled: Boolean = false,
    val isPlaying: Boolean = false,
    val phase: ReplacementPhase = ReplacementPhase.DISABLED,
    val statusMessage: String = "Select at least 5 local tracks to get started.",
    val currentTrackName: String? = null,
    val currentAdLabel: String? = null,
    val adVolumeStep: Int = 1,
    val maxMediaVolume: Int = 15,
    val replacementVolumePercent: Int = 70,
    val shuffle: Boolean = true,
    val originalMediaVolume: Int? = null,
    val appliedMediaVolume: Int? = null,
    val blocksHandled: Int = 0,
    val replacedDurationMs: Long = 0L
)

object ReplacementStore {
    private val _state = MutableStateFlow(ReplacementState())
    val state: StateFlow<ReplacementState> = _state.asStateFlow()

    fun initialize(context: Context) {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val tracks = ReplacementPreferences.tracks(context)
        val enabled = ReplacementPreferences.isEnabled(context) && tracks.size >= MIN_REPLACEMENT_TRACKS
        if (!enabled && ReplacementPreferences.isEnabled(context)) ReplacementPreferences.setEnabled(context, false)
        _state.value = ReplacementState(
            tracks = tracks,
            enabled = enabled,
            phase = if (enabled) ReplacementPhase.WAITING else ReplacementPhase.DISABLED,
            statusMessage = when {
                tracks.size < MIN_REPLACEMENT_TRACKS -> "Select at least $MIN_REPLACEMENT_TRACKS local tracks to get started."
                enabled -> "Replacement enabled. Random library preloaded."
                else -> "Random library ready. Enable AdScope whenever you want."
            },
            adVolumeStep = ReplacementPreferences.adVolumeStep(context)
                .coerceIn(0, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)),
            maxMediaVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1),
            replacementVolumePercent = ReplacementPreferences.replacementVolumePercent(context),
            shuffle = ReplacementPreferences.shuffle(context),
            blocksHandled = ReplacementPreferences.blocksHandled(context),
            replacedDurationMs = ReplacementPreferences.replacedDurationMs(context)
        )
    }

    fun setTracks(tracks: List<ReplacementTrack>) {
        _state.update {
            it.copy(
                tracks = tracks,
                currentTrackName = tracks.firstOrNull()?.name,
                statusMessage = if (tracks.size < MIN_REPLACEMENT_TRACKS) {
                    "Select at least $MIN_REPLACEMENT_TRACKS local tracks to get started."
                } else if (it.enabled) {
                    "Library updated. Waiting for a Spotify ad."
                } else {
                    "${tracks.size} tracks ready in random order. Enable AdScope whenever you want."
                }
            )
        }
    }

    fun setEnabled(enabled: Boolean, message: String) {
        _state.update {
            it.copy(
                enabled = enabled,
                isPlaying = if (enabled) it.isPlaying else false,
                phase = if (enabled) ReplacementPhase.WAITING else ReplacementPhase.DISABLED,
                currentAdLabel = if (enabled) it.currentAdLabel else null,
                originalMediaVolume = if (enabled) it.originalMediaVolume else null,
                appliedMediaVolume = if (enabled) it.appliedMediaVolume else null,
                statusMessage = message
            )
        }
    }

    fun setPhase(
        phase: ReplacementPhase,
        message: String,
        isPlaying: Boolean = _state.value.isPlaying,
        currentTrackName: String? = _state.value.currentTrackName,
        currentAdLabel: String? = _state.value.currentAdLabel
    ) {
        _state.update {
            it.copy(
                phase = phase,
                statusMessage = message,
                isPlaying = isPlaying,
                currentTrackName = currentTrackName,
                currentAdLabel = currentAdLabel
            )
        }
    }

    fun setVolumeSession(original: Int?, applied: Int?) {
        _state.update { it.copy(originalMediaVolume = original, appliedMediaVolume = applied) }
    }

    fun setConfiguration(adVolumeStep: Int, replacementVolumePercent: Int, shuffle: Boolean) {
        _state.update {
            it.copy(
                adVolumeStep = adVolumeStep,
                replacementVolumePercent = replacementVolumePercent,
                shuffle = shuffle
            )
        }
    }

    fun setCurrentTrack(name: String?) {
        _state.update { it.copy(currentTrackName = name) }
    }

    fun setStatistics(blocks: Int, durationMs: Long) {
        _state.update { it.copy(blocksHandled = blocks, replacedDurationMs = durationMs) }
    }
}

object ReplacementPreferences {
    private const val PREFS = "adscope_replacement"
    private const val KEY_TRACKS = "replacement_tracks"
    private const val KEY_ENABLED = "replacement_enabled"
    private const val KEY_AD_VOLUME = "ad_volume_step"
    private const val KEY_REPLACEMENT_VOLUME = "replacement_volume_percent"
    private const val KEY_SHUFFLE = "shuffle"
    private const val KEY_CURRENT_INDEX = "current_track_index"
    private const val KEY_CURRENT_POSITION = "current_track_position"
    private const val KEY_BLOCKS = "blocks_handled"
    private const val KEY_DURATION = "replaced_duration_ms"

    fun saveTracks(context: Context, tracks: List<ReplacementTrack>) {
        val array = JSONArray()
        tracks.forEach { track ->
            array.put(JSONObject().put("uri", track.uri).put("name", track.name))
        }
        prefs(context).edit().putString(KEY_TRACKS, array.toString()).apply()
    }

    fun tracks(context: Context): List<ReplacementTrack> {
        val raw = prefs(context).getString(KEY_TRACKS, null) ?: return migrateLegacyTrack(context)
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val uri = item.optString("uri")
                    val name = item.optString("name", "Local track")
                    if (uri.isNotBlank()) add(ReplacementTrack(uri, name))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun migrateLegacyTrack(context: Context): List<ReplacementTrack> {
        val legacyUri = prefs(context).getString("selected_track_uri", null) ?: return emptyList()
        val legacyName = prefs(context).getString("selected_track_name", "Local track") ?: "Local track"
        val migrated = listOf(ReplacementTrack(legacyUri, legacyName))
        saveTracks(context, migrated)
        return migrated
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setAdVolumeStep(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_AD_VOLUME, value.coerceAtLeast(0)).apply()
    }

    fun adVolumeStep(context: Context): Int = prefs(context).getInt(KEY_AD_VOLUME, 1).coerceAtLeast(0)

    fun setReplacementVolumePercent(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_REPLACEMENT_VOLUME, value.coerceIn(10, 100)).apply()
    }

    fun replacementVolumePercent(context: Context): Int =
        prefs(context).getInt(KEY_REPLACEMENT_VOLUME, 70).coerceIn(10, 100)

    fun setShuffle(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHUFFLE, enabled).apply()
    }

    fun shuffle(context: Context): Boolean = prefs(context).getBoolean(KEY_SHUFFLE, true)

    fun savePlaybackPosition(context: Context, index: Int, positionMs: Long) {
        prefs(context).edit()
            .putInt(KEY_CURRENT_INDEX, index.coerceAtLeast(0))
            .putLong(KEY_CURRENT_POSITION, positionMs.coerceAtLeast(0L))
            .apply()
    }

    fun playbackIndex(context: Context): Int = prefs(context).getInt(KEY_CURRENT_INDEX, 0).coerceAtLeast(0)

    fun playbackPosition(context: Context): Long = prefs(context).getLong(KEY_CURRENT_POSITION, 0L).coerceAtLeast(0L)

    fun incrementBlocks(context: Context): Int {
        val value = blocksHandled(context) + 1
        prefs(context).edit().putInt(KEY_BLOCKS, value).apply()
        return value
    }

    fun blocksHandled(context: Context): Int = prefs(context).getInt(KEY_BLOCKS, 0).coerceAtLeast(0)

    fun addReplacedDuration(context: Context, durationMs: Long): Long {
        val total = replacedDurationMs(context) + durationMs.coerceAtLeast(0L)
        prefs(context).edit().putLong(KEY_DURATION, total).apply()
        return total
    }

    fun replacedDurationMs(context: Context): Long = prefs(context).getLong(KEY_DURATION, 0L).coerceAtLeast(0L)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

data class SavedVolumeSession(
    val originalMedia: Int,
    val appliedMedia: Int,
    val originalAlarm: Int,
    val appliedAlarm: Int,
    val startedAt: Long
)

data class VolumeRestoreResult(
    val restoredMedia: Boolean,
    val restoredAlarm: Boolean,
    val manualMediaChangeDetected: Boolean,
    val manualAlarmChangeDetected: Boolean
)

object VolumeSafety {
    private const val PREFS = "adscope_volume_safety"
    private const val KEY_ACTIVE = "active"
    private const val KEY_ORIGINAL_MEDIA = "original_media"
    private const val KEY_APPLIED_MEDIA = "applied_media"
    private const val KEY_ORIGINAL_ALARM = "original_alarm"
    private const val KEY_APPLIED_ALARM = "applied_alarm"
    private const val KEY_STARTED_AT = "started_at"

    fun begin(
        context: Context,
        audioManager: AudioManager,
        targetMediaVolume: Int,
        targetAlarmVolume: Int
    ): SavedVolumeSession {
        recoverIfNeeded(context, audioManager, force = false)

        val mediaMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val alarmMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1)
        val session = SavedVolumeSession(
            originalMedia = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
            appliedMedia = targetMediaVolume.coerceIn(0, mediaMax),
            originalAlarm = audioManager.getStreamVolume(AudioManager.STREAM_ALARM),
            appliedAlarm = targetAlarmVolume.coerceIn(1, alarmMax),
            startedAt = System.currentTimeMillis()
        )

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putInt(KEY_ORIGINAL_MEDIA, session.originalMedia)
            .putInt(KEY_APPLIED_MEDIA, session.appliedMedia)
            .putInt(KEY_ORIGINAL_ALARM, session.originalAlarm)
            .putInt(KEY_APPLIED_ALARM, session.appliedAlarm)
            .putLong(KEY_STARTED_AT, session.startedAt)
            .commit()

        return session
    }

    fun peek(context: Context): SavedVolumeSession? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return null
        return SavedVolumeSession(
            originalMedia = prefs.getInt(KEY_ORIGINAL_MEDIA, 1),
            appliedMedia = prefs.getInt(KEY_APPLIED_MEDIA, 1),
            originalAlarm = prefs.getInt(KEY_ORIGINAL_ALARM, 1),
            appliedAlarm = prefs.getInt(KEY_APPLIED_ALARM, 1),
            startedAt = prefs.getLong(KEY_STARTED_AT, 0L)
        )
    }

    fun recoverIfNeeded(
        context: Context,
        audioManager: AudioManager = context.getSystemService(AudioManager::class.java),
        force: Boolean = false
    ): VolumeRestoreResult? {
        val session = peek(context) ?: return null
        val currentMedia = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val currentAlarm = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val restoreMedia = force || currentMedia == session.appliedMedia
        val restoreAlarm = force || currentAlarm == session.appliedAlarm

        if (restoreMedia) {
            runCatching {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, session.originalMedia, 0)
            }
        }
        if (restoreAlarm) {
            runCatching {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, session.originalAlarm, 0)
            }
        }
        clear(context)

        return VolumeRestoreResult(
            restoredMedia = restoreMedia,
            restoredAlarm = restoreAlarm,
            manualMediaChangeDetected = !restoreMedia,
            manualAlarmChangeDetected = !restoreAlarm
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }
}
