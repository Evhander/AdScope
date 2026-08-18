package com.finaticlabs.adscope

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.finaticlabs.adscope.monitor.MediaMonitorStore
import com.finaticlabs.adscope.monitor.MediaNotificationListener
import com.finaticlabs.adscope.playback.AdReplacementService
import com.finaticlabs.adscope.playback.MIN_REPLACEMENT_TRACKS
import com.finaticlabs.adscope.playback.ReplacementPreferences
import com.finaticlabs.adscope.playback.ReplacementStore
import com.finaticlabs.adscope.playback.ReplacementTrack
import com.finaticlabs.adscope.playback.VolumeSafety
import com.finaticlabs.adscope.ui.AdScopeApp
import com.finaticlabs.adscope.ui.theme.AdScopeTheme

class MainActivity : ComponentActivity() {

    private enum class PendingOperation { NONE, ENABLE, TEST }

    private var notificationAccessGranted by mutableStateOf(false)
    private var notificationPermissionGranted by mutableStateOf(false)
    private var pendingOperation = PendingOperation.NONE

    private val audioPicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) saveSelectedTracks(uris)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionGranted = granted
        val operation = pendingOperation
        pendingOperation = PendingOperation.NONE
        if (granted) {
            when (operation) {
                PendingOperation.ENABLE -> enableReplacement()
                PendingOperation.TEST -> runConfigurationTest()
                PendingOperation.NONE -> Unit
            }
        } else if (operation != PendingOperation.NONE) {
            Toast.makeText(
                this,
                "AdScope needs to show a notification while running in the background.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AdReplacementService.isRunning() && VolumeSafety.peek(this) != null) {
            VolumeSafety.recoverIfNeeded(this, force = false)
        }

        notificationAccessGranted = hasNotificationAccess()
        notificationPermissionGranted = hasNotificationPermission()
        ReplacementStore.initialize(this)

        setContent {
            AdScopeTheme {
                AdScopeApp(
                    notificationAccessGranted = notificationAccessGranted,
                    monitorState = MediaMonitorStore.state,
                    replacementState = ReplacementStore.state,
                    onOpenNotificationSettings = ::openNotificationAccessSettings,
                    onRefresh = { MediaNotificationListener.requestRefresh() },
                    onClearHistory = MediaMonitorStore::clearHistory,
                    onCopyDiagnostics = ::copyDiagnostics,
                    onChooseTracks = { audioPicker.launch(arrayOf("audio/*")) },
                    onToggleReplacement = ::toggleReplacement,
                    onAdVolumeChanged = ::setAdVolume,
                    onReplacementVolumeChanged = ::setReplacementVolume,
                    onTestConfiguration = ::requestConfigurationTest,
                    onRestoreVolume = ::restoreVolumeNow
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        notificationAccessGranted = hasNotificationAccess()
        notificationPermissionGranted = hasNotificationPermission()
        if (notificationAccessGranted) {
            MediaNotificationListener.requestRefresh()
        }
        if (
            notificationAccessGranted &&
            notificationPermissionGranted &&
            ReplacementPreferences.isEnabled(this) &&
            ReplacementPreferences.tracks(this).size >= MIN_REPLACEMENT_TRACKS &&
            !AdReplacementService.isRunning()
        ) {
            runCatching { AdReplacementService.start(this) }
        }
    }

    private fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openNotificationAccessSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun toggleReplacement(enabled: Boolean) {
        if (!enabled) {
            AdReplacementService.stop(this)
            return
        }

        if (!notificationAccessGranted) {
            Toast.makeText(
                this,
                "You must grant notification access first.",
                Toast.LENGTH_LONG
            ).show()
            openNotificationAccessSettings()
            return
        }

        if (ReplacementPreferences.tracks(this).size < MIN_REPLACEMENT_TRACKS) {
            Toast.makeText(
                this,
                "Select at least $MIN_REPLACEMENT_TRACKS tracks.",
                Toast.LENGTH_LONG
            ).show()
            audioPicker.launch(arrayOf("audio/*"))
            return
        }

        runAfterNotificationPermission(PendingOperation.ENABLE)
    }

    private fun enableReplacement() {
        runCatching { AdReplacementService.start(this) }
            .onFailure { showServiceError(it) }
    }

    private fun requestConfigurationTest() {
        if (ReplacementPreferences.tracks(this).size < MIN_REPLACEMENT_TRACKS) {
            Toast.makeText(this, "Select at least $MIN_REPLACEMENT_TRACKS tracks before testing.", Toast.LENGTH_LONG).show()
            audioPicker.launch(arrayOf("audio/*"))
            return
        }
        runAfterNotificationPermission(PendingOperation.TEST)
    }

    private fun runConfigurationTest() {
        runCatching { AdReplacementService.testConfiguration(this) }
            .onFailure { showServiceError(it) }
    }

    private fun runAfterNotificationPermission(operation: PendingOperation) {
        if (!notificationPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingOperation = operation
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            when (operation) {
                PendingOperation.ENABLE -> enableReplacement()
                PendingOperation.TEST -> runConfigurationTest()
                PendingOperation.NONE -> Unit
            }
        }
    }

    private fun restoreVolumeNow() {
        if (AdReplacementService.isRunning() || VolumeSafety.peek(this) != null) {
            runCatching { AdReplacementService.restoreVolume(this) }
                .onFailure {
                    VolumeSafety.recoverIfNeeded(this, force = true)
                    ReplacementStore.initialize(this)
                }
        } else {
            Toast.makeText(this, "There is no pending volume to restore.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setAdVolume(value: Int) {
        val safeValue = value.coerceAtLeast(0)
        ReplacementPreferences.setAdVolumeStep(this, safeValue)
        val current = ReplacementStore.state.value
        ReplacementStore.setConfiguration(
            safeValue,
            current.replacementVolumePercent,
            current.shuffle
        )
        reloadRunningService()
    }

    private fun setReplacementVolume(value: Int) {
        val safeValue = value.coerceIn(10, 100)
        ReplacementPreferences.setReplacementVolumePercent(this, safeValue)
        val current = ReplacementStore.state.value
        ReplacementStore.setConfiguration(current.adVolumeStep, safeValue, current.shuffle)
        reloadRunningService()
    }

    private fun reloadRunningService() {
        if (AdReplacementService.isRunning() || ReplacementPreferences.isEnabled(this)) {
            runCatching { AdReplacementService.reloadConfiguration(this) }
        }
    }

    private fun saveSelectedTracks(uris: List<Uri>) {
        val oldUris = ReplacementPreferences.tracks(this).map { it.uri }.toSet()
        val tracks = uris.distinct().mapNotNull { uri ->
            val persisted = runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                true
            }.getOrElse { false }

            if (!persisted) {
                Toast.makeText(
                    this,
                    "Could not keep access to ${queryDisplayName(uri) ?: "a file"}.",
                    Toast.LENGTH_LONG
                ).show()
            }
            ReplacementTrack(uri.toString(), queryDisplayName(uri) ?: "Local track")
        }

        if (tracks.isEmpty()) return

        if (tracks.size < MIN_REPLACEMENT_TRACKS) {
            tracks.filterNot { it.uri in oldUris }.forEach { track ->
                runCatching {
                    contentResolver.releasePersistableUriPermission(
                        Uri.parse(track.uri),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            Toast.makeText(
                this,
                "Select at least $MIN_REPLACEMENT_TRACKS tracks. You selected ${tracks.size}.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val newUris = tracks.map { it.uri }.toSet()
        oldUris.filterNot { it in newUris }.forEach { oldUri ->
            runCatching {
                contentResolver.releasePersistableUriPermission(
                    Uri.parse(oldUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }

        ReplacementPreferences.saveTracks(this, tracks)
        ReplacementPreferences.savePlaybackPosition(this, 0, 0L)
        ReplacementStore.setTracks(tracks)
        Toast.makeText(
            this,
            "Library updated: ${tracks.size} tracks in random order.",
            Toast.LENGTH_SHORT
        ).show()
        reloadRunningService()
    }

    private fun queryDisplayName(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )
            if (cursor?.moveToFirst() == true) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        } finally {
            cursor?.close()
        }
    }

    private fun copyDiagnostics(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AdScope diagnostic", text))
        Toast.makeText(this, "Diagnostics copied", Toast.LENGTH_SHORT).show()
    }

    private fun showServiceError(error: Throwable) {
        Toast.makeText(
            this,
            "Could not start the service: ${error.message.orEmpty()}",
            Toast.LENGTH_LONG
        ).show()
    }
}
