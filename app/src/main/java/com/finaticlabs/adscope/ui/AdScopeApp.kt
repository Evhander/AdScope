package com.finaticlabs.adscope.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finaticlabs.adscope.monitor.DetectionLevel
import com.finaticlabs.adscope.monitor.MediaMonitorStore
import com.finaticlabs.adscope.monitor.MediaSnapshot
import com.finaticlabs.adscope.monitor.MonitorState
import com.finaticlabs.adscope.playback.MIN_REPLACEMENT_TRACKS
import com.finaticlabs.adscope.playback.ReplacementPhase
import com.finaticlabs.adscope.playback.ReplacementState
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdScopeApp(
    notificationAccessGranted: Boolean,
    monitorState: StateFlow<MonitorState>,
    replacementState: StateFlow<ReplacementState>,
    onOpenNotificationSettings: () -> Unit,
    onRefresh: () -> Unit,
    onClearHistory: () -> Unit,
    onCopyDiagnostics: (String) -> Unit,
    onChooseTracks: () -> Unit,
    onToggleReplacement: (Boolean) -> Unit,
    onAdVolumeChanged: (Int) -> Unit,
    onReplacementVolumeChanged: (Int) -> Unit,
    onTestConfiguration: () -> Unit,
    onRestoreVolume: () -> Unit
) {
    val monitor by monitorState.collectAsState()
    val replacement by replacementState.collectAsState()
    var advancedExpanded by remember { mutableStateOf(false) }
    var suspiciousOnly by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("AdScope", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Spotify ad replacement",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                MainStatusCard(
                    notificationAccessGranted = notificationAccessGranted,
                    monitor = monitor,
                    replacement = replacement,
                    onOpenSettings = onOpenNotificationSettings,
                    onToggleReplacement = onToggleReplacement
                )
            }

            item {
                SectionHeader(title = "Playback control", icon = Icons.Rounded.PlayCircle)
                PlaybackCard(
                    state = replacement,
                    onChooseTracks = onChooseTracks,
                    onTestConfiguration = onTestConfiguration,
                    onRestoreVolume = onRestoreVolume
                )
            }

            item {
                SectionHeader(title = "Audio mix", icon = Icons.Rounded.Tune)
                AudioSettingsCard(
                    state = replacement,
                    onAdVolumeChanged = onAdVolumeChanged,
                    onReplacementVolumeChanged = onReplacementVolumeChanged
                )
            }

            item {
                SectionHeader(title = "Statistics", icon = Icons.Rounded.BarChart)
                StatsCard(replacement)
            }

            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { advancedExpanded = !advancedExpanded },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            if (advancedExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (advancedExpanded) "Hide advanced diagnostics" else "Show advanced diagnostics",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (advancedExpanded) {
                item { PrivacyCard() }
                
                item { 
                    SectionHeader(
                        title = "Current sessions (${monitor.activeSessionCount})", 
                        icon = Icons.Rounded.Devices 
                    ) 
                }

                if (monitor.currentSessions.isEmpty()) {
                    item {
                        EmptyCard(
                            if (notificationAccessGranted) {
                                "Play music in Spotify to get started."
                            } else {
                                "Enable notification access to detect sessions."
                            }
                        )
                    }
                } else {
                    items(monitor.currentSessions, key = { it.sessionId }) { session ->
                        MediaCard(session, showReasons = true)
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SectionHeader(title = "Technical history", icon = Icons.AutoMirrored.Rounded.Notes)
                        FilterChip(
                            selected = suspiciousOnly,
                            onClick = { suspiciousOnly = !suspiciousOnly },
                            label = { Text("Ads only") },
                            leadingIcon = if (suspiciousOnly) {
                                { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }

                val visibleHistory = if (suspiciousOnly) {
                    monitor.history.filter { it.assessment.level != DetectionLevel.NORMAL }
                } else monitor.history

                if (visibleHistory.isEmpty()) {
                    item { EmptyCard("No recorded events.") }
                } else {
                    items(visibleHistory, key = { "${it.sessionId}:${it.detectedAt}" }) { event ->
                        MediaCard(event, showReasons = false)
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onClearHistory, 
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Clear")
                        }
                        Button(
                            onClick = { onCopyDiagnostics(MediaMonitorStore.diagnosticsText(monitor)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Copy")
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    "AdScope v1.3.7 FULL MUTE · Experimental project\nNot affiliated with Spotify.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, 
            contentDescription = null, 
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun MainStatusCard(
    notificationAccessGranted: Boolean,
    monitor: MonitorState,
    replacement: ReplacementState,
    onOpenSettings: () -> Unit,
    onToggleReplacement: (Boolean) -> Unit
) {
    val active = replacement.phase == ReplacementPhase.AD_ACTIVE || replacement.phase == ReplacementPhase.TESTING
    
    val containerColor by animateColorAsState(
        targetValue = when {
            active -> MaterialTheme.colorScheme.primary
            replacement.enabled && monitor.serviceConnected -> MaterialTheme.colorScheme.secondaryContainer
            !notificationAccessGranted -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(500)
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (active) 8.dp else 0.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (active) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                active -> Icons.Rounded.RecordVoiceOver
                                replacement.enabled -> Icons.Rounded.Shield
                                else -> Icons.Rounded.ShieldMoon
                            },
                            contentDescription = null,
                            tint = if (active) Color.White else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            when {
                                active -> replacement.currentAdLabel ?: "Replacing ad"
                                replacement.enabled -> "Monitoring active"
                                else -> "Service paused"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            phaseLabel(replacement.phase), 
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = replacement.enabled,
                    onCheckedChange = onToggleReplacement,
                    enabled = notificationAccessGranted || replacement.enabled
                )
            }

            AnimatedContent(
                targetState = replacement.statusMessage,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "statusMessage"
            ) { targetMessage ->
                Surface(
                    color = if (active) Color.Black.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        targetMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (!notificationAccessGranted) {
                Button(
                    onClick = onOpenSettings, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Rounded.LockOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Allow notification access")
                }
            }
        }
    }
}

@Composable
private fun PlaybackCard(
    state: ReplacementState,
    onChooseTracks: () -> Unit,
    onTestConfiguration: () -> Unit,
    onRestoreVolume: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Track Info
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.MusicNote, 
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        state.currentTrackName ?: "No track playing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (state.tracks.isEmpty()) "Empty library" else "${state.tracks.size} tracks in library",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.originalMediaVolume != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeDown, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Spotify volume", style = MaterialTheme.typography.labelMedium)
                        }
                        Text(
                            "${state.originalMediaVolume} → ${state.appliedMediaVolume}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onChooseTracks, 
                    modifier = Modifier.weight(1.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (state.tracks.size < MIN_REPLACEMENT_TRACKS) "Choose 5+" else "Library", maxLines = 1)
                }
                Button(
                    onClick = onTestConfiguration,
                    enabled = state.tracks.size >= MIN_REPLACEMENT_TRACKS && !state.isPlaying,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Test")
                }
            }

            TextButton(
                onClick = onRestoreVolume, 
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Rounded.SettingsBackupRestore, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Restore volumes manually")
            }
        }
    }
}

@Composable
private fun AudioSettingsCard(
    state: ReplacementState,
    onAdVolumeChanged: (Int) -> Unit,
    onReplacementVolumeChanged: (Int) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            
            SliderSetting(
                label = "Ad volume",
                value = state.adVolumeStep.toFloat(),
                range = 0f..state.maxMediaVolume.toFloat(),
                displayValue = if (state.adVolumeStep == 0) "MUTE" else "${state.adVolumeStep}",
                icon = Icons.AutoMirrored.Rounded.VolumeMute,
                onValueChange = { onAdVolumeChanged(it.roundToInt().coerceAtLeast(0)) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)

            SliderSetting(
                label = "Local music volume",
                value = state.replacementVolumePercent.toFloat(),
                range = 10f..100f,
                displayValue = "${state.replacementVolumePercent}%",
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                onValueChange = {
                    val rounded = ((it / 5f).roundToInt() * 5).coerceIn(10, 100)
                    onReplacementVolumeChanged(rounded)
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Shuffle, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Required random playback enabled",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    displayValue: String,
    icon: ImageVector,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                displayValue, 
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StatsCard(state: ReplacementState) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                label = "Blocks", 
                value = state.blocksHandled.toString(), 
                icon = Icons.Rounded.Layers
            )
            VerticalDivider(modifier = Modifier.height(40.dp))
            StatItem(
                label = "Replaced", 
                value = formatLongDuration(state.replacedDurationMs), 
                icon = Icons.Rounded.Timer
            )
            VerticalDivider(modifier = Modifier.height(40.dp))
            StatItem(
                label = "Status", 
                value = shortPhase(state.phase), 
                icon = Icons.Rounded.Info
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PrivacyCard() {
    Surface(
        shape = RoundedCornerShape(16.dp), 
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Privacy & Security", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "AdScope runs locally. It does not require Internet or Accessibility permission.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun MediaCard(session: MediaSnapshot, showReasons: Boolean) {
    val levelColor = when (session.assessment.level) {
        DetectionLevel.POSSIBLE_AD -> MaterialTheme.colorScheme.error
        DetectionLevel.SUSPICIOUS -> MaterialTheme.colorScheme.tertiary
        DetectionLevel.NORMAL -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, levelColor.copy(alpha = 0.2f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        if (session.assessment.level != DetectionLevel.NORMAL) Icons.Rounded.NewReleases else Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = levelColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(session.appName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                }
                ScoreBadge(session, levelColor)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                Text(
                    session.title ?: "Untitled", 
                    fontWeight = FontWeight.Bold, 
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${session.artist ?: "Unknown artist"} • ${session.album ?: "Unknown album"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailInfo(Icons.Rounded.Timer, formatDuration(session.durationMs))
                DetailInfo(Icons.Rounded.History, formatTime(session.detectedAt))
                DetailInfo(
                    if (session.isPlaying) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, 
                    session.playbackState
                )
            }

            if (showReasons) {
                HorizontalDivider(thickness = 0.5.dp)
                session.assessment.reasons.forEach { reason ->
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 1.dp)) {
                        Text("•", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(reason, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailInfo(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScoreBadge(session: MediaSnapshot, color: Color) {
    Surface(
        shape = CircleShape, 
        color = color.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = "${session.assessment.score}",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
    }
}

@Composable
private fun EmptyCard(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.Inbox, 
            contentDescription = null, 
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text, 
            style = MaterialTheme.typography.bodyMedium, 
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun phaseLabel(phase: ReplacementPhase): String = when (phase) {
    ReplacementPhase.DISABLED -> "Service stopped"
    ReplacementPhase.WAITING -> "Waiting for ad..."
    ReplacementPhase.AD_ACTIVE -> "Replacing audio"
    ReplacementPhase.RESTORING -> "Restoring volume"
    ReplacementPhase.TESTING -> "Test mode"
    ReplacementPhase.ERROR -> "Error detected"
}

private fun shortPhase(phase: ReplacementPhase): String = when (phase) {
    ReplacementPhase.AD_ACTIVE -> "Active"
    ReplacementPhase.TESTING -> "Test"
    ReplacementPhase.WAITING -> "Ready"
    ReplacementPhase.RESTORING -> "Restoring"
    ReplacementPhase.ERROR -> "Error"
    ReplacementPhase.DISABLED -> "Off"
}

private fun formatDuration(durationMs: Long?): String {
    if (durationMs == null) return "—"
    val totalSeconds = durationMs / 1_000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

private fun formatLongDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}

private fun formatTime(time: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
