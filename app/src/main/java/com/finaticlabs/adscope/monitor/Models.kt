package com.finaticlabs.adscope.monitor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DetectionLevel {
    NORMAL,
    SUSPICIOUS,
    POSSIBLE_AD
}

data class AdAssessment(
    val score: Int,
    val level: DetectionLevel,
    val reasons: List<String>
)

data class MediaSnapshot(
    val sessionId: String,
    val packageName: String,
    val appName: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val playbackState: String,
    val isPlaying: Boolean,
    val canSeek: Boolean,
    val canSkipNext: Boolean,
    val assessment: AdAssessment,
    val detectedAt: Long = System.currentTimeMillis()
) {
    val fingerprint: String
        get() = listOf(
            sessionId,
            title.orEmpty(),
            artist.orEmpty(),
            album.orEmpty(),
            durationMs?.toString().orEmpty()
        ).joinToString("|")
}

data class MonitorState(
    val serviceConnected: Boolean = false,
    val activeSessionCount: Int = 0,
    val currentSessions: List<MediaSnapshot> = emptyList(),
    val history: List<MediaSnapshot> = emptyList(),
    val statusMessage: String = "Grant notification access to get started."
)

object MediaMonitorStore {
    private const val MAX_HISTORY = 200

    private val _state = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    private val lastFingerprintBySession = mutableMapOf<String, String>()

    @Synchronized
    fun setServiceConnected(connected: Boolean, message: String) {
        _state.update {
            it.copy(
                serviceConnected = connected,
                statusMessage = message,
                activeSessionCount = if (connected) it.activeSessionCount else 0,
                currentSessions = if (connected) it.currentSessions else emptyList()
            )
        }
    }

    @Synchronized
    fun setActiveSessions(sessionIds: Set<String>) {
        lastFingerprintBySession.keys.retainAll(sessionIds)
        _state.update { current ->
            current.copy(
                activeSessionCount = sessionIds.size,
                currentSessions = current.currentSessions.filter { it.sessionId in sessionIds },
                statusMessage = when {
                    !current.serviceConnected -> current.statusMessage
                    sessionIds.isEmpty() -> "Service connected. No active media sessions."
                    else -> "Analyzing ${sessionIds.size} media session(s)."
                }
            )
        }
    }

    @Synchronized
    fun publish(snapshot: MediaSnapshot) {
        val previousFingerprint = lastFingerprintBySession[snapshot.sessionId]
        val changed = previousFingerprint != snapshot.fingerprint
        lastFingerprintBySession[snapshot.sessionId] = snapshot.fingerprint

        _state.update { current ->
            val updatedCurrent = (
                current.currentSessions.filterNot { it.sessionId == snapshot.sessionId } + snapshot
            ).sortedByDescending { it.isPlaying }

            val updatedHistory = if (changed) {
                (listOf(snapshot) + current.history).take(MAX_HISTORY)
            } else {
                current.history
            }

            current.copy(
                currentSessions = updatedCurrent,
                history = updatedHistory,
                statusMessage = "Last update: ${formatClock(snapshot.detectedAt)}"
            )
        }
    }

    @Synchronized
    fun clearHistory() {
        _state.update { it.copy(history = emptyList()) }
    }

    fun diagnosticsText(state: MonitorState): String = buildString {
        appendLine("AdScope diagnostic")
        appendLine("Service connected: ${state.serviceConnected}")
        appendLine("Active sessions: ${state.activeSessionCount}")
        appendLine("Status: ${state.statusMessage}")
        appendLine()
        appendLine("History (${state.history.size})")
        state.history.forEach { item ->
            appendLine("---")
            appendLine("Time: ${formatDateTime(item.detectedAt)}")
            appendLine("App: ${item.appName} (${item.packageName})")
            appendLine("Title: ${item.title.orEmpty()}")
            appendLine("Artist: ${item.artist.orEmpty()}")
            appendLine("Album: ${item.album.orEmpty()}")
            appendLine("Duration ms: ${item.durationMs ?: "unknown"}")
            appendLine("Playback: ${item.playbackState}")
            appendLine("Can seek: ${item.canSeek}")
            appendLine("Can skip next: ${item.canSkipNext}")
            appendLine("Ad score: ${item.assessment.score}")
            appendLine("Classification: ${item.assessment.level}")
            appendLine("Reasons: ${item.assessment.reasons.joinToString("; ")}")
        }
    }

    private fun formatClock(time: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(time))

    private fun formatDateTime(time: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time))
}
