package com.linzin.linzindeskclock.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.linzin.linzindeskclock.R
import com.linzin.linzindeskclock.media.AlbumArtBackground
import com.linzin.linzindeskclock.media.AlbumArtGlassBackground
import com.linzin.linzindeskclock.media.MediaCommand
import com.linzin.linzindeskclock.media.MediaSessionSnapshot
import com.linzin.linzindeskclock.media.PlaybackPhase
import com.linzin.linzindeskclock.media.primaryCommand
import kotlinx.coroutines.delay
import java.util.Locale

private val MediaSurface = Color(0xFF1E1E22)
private val MediaMuted = Color.White.copy(alpha = 0.72f)

@Composable
internal fun MediaPresentation(session: MediaSessionSnapshot, controlsEnabled: Boolean, onCommand: (MediaCommand, Long?) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Keep the user's bottom-panel composition at every aspect ratio. At 300 dp, 120 dp
        // fits two metadata lines, a 32 dp seek target and 48 dp transport without scrolling.
        // The upper cap always wins, including tiny viewports and accessibility font scales.
        val panelHeight = (maxHeight * 0.35f).coerceAtLeast(120.dp).coerceAtMost(maxHeight * 0.40f)
        Column(Modifier.fillMaxSize()) {
            AlbumArtBackground(session.artwork, Modifier.weight(1f).fillMaxWidth().testTag("media_artwork"))
            MediaGlassPanel(session, controlsEnabled, onCommand, Modifier.fillMaxWidth().height(panelHeight))
        }
    }
}

@Composable
private fun MediaGlassPanel(session: MediaSessionSnapshot, controlsEnabled: Boolean,
    onCommand: (MediaCommand, Long?) -> Unit, modifier: Modifier) {
    // MediaWidgetContent owns the sole rounded outer clip. The inner cover/panel seam must
    // remain fully painted, including its corners; a second round clip exposes black wedges.
    Box(modifier.background(MediaSurface).testTag("media_glass_panel")) {
        AlbumArtGlassBackground(session.artwork, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(MediaSurface.copy(alpha = 0.30f))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.03f))))
            .border(1.dp, Color.White.copy(alpha = 0.12f)))
        MediaDetails(session, controlsEnabled, onCommand, Modifier.fillMaxSize())
    }
}

@Composable
private fun MediaDetails(session: MediaSessionSnapshot, controlsEnabled: Boolean,
    onCommand: (MediaCommand, Long?) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val tight = maxHeight < 132.dp
        val compact = maxHeight < 180.dp
        val narrow = maxWidth < 240.dp
        val padding = if (narrow) 8.dp else 16.dp
        val verticalPadding = if (tight) 2.dp else if (compact) 4.dp else 8.dp
        val titleStyle = when {
            tight -> MaterialTheme.typography.titleSmall
            compact || narrow -> MaterialTheme.typography.titleMedium
            else -> MaterialTheme.typography.titleLarge
        }
        val artistStyle = when {
            tight -> MaterialTheme.typography.labelSmall
            compact -> MaterialTheme.typography.bodySmall
            else -> MaterialTheme.typography.bodyMedium
        }
        val shadow = with(LocalDensity.current) { Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1.dp.toPx()), 4.dp.toPx()) }
        val titleLines = if (maxHeight >= 232.dp) 2 else 1
        val largePrimary = maxHeight >= 152.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = padding, vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 2.dp, Alignment.CenterVertically)) {
            Text(session.title.ifBlank { stringResource(R.string.media_unknown_title) }, color = Color.White,
                style = titleStyle.copy(shadow = shadow), fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = titleLines, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().testTag("media_title"))
            Text(session.artist.ifBlank { stringResource(R.string.media_unknown_artist) }, color = MediaMuted,
                style = artistStyle.copy(shadow = shadow), textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().testTag("media_artist"))
            val status = when (session.playback.phase) {
                PlaybackPhase.BUFFERING, PlaybackPhase.SKIPPING -> R.string.media_state_buffering
                PlaybackPhase.CONNECTING -> R.string.media_state_connecting
                PlaybackPhase.ERROR -> R.string.media_state_error
                else -> null
            }
            if (status != null) Text(stringResource(status), color = MediaMuted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            MediaProgress(session, controlsEnabled, onCommand, if (compact) 32.dp else 40.dp)
            MediaTransport(session, controlsEnabled, onCommand, largePrimary)
            val hasControls = primaryCommand(session.playback.phase, session.actions) != null || session.actions.previous || session.actions.next
            val hint = when {
                !controlsEnabled -> R.string.media_controls_ui_locked
                !hasControls && !session.actions.seek -> R.string.media_controls_unavailable
                else -> null
            }
            if (hint != null) Text(stringResource(hint), color = MediaMuted, style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MediaTransport(session: MediaSessionSnapshot, enabled: Boolean, onCommand: (MediaCommand, Long?) -> Unit, largePrimary: Boolean) {
    val primary = primaryCommand(session.playback.phase, session.actions)
    if (primary == null && !session.actions.previous && !session.actions.next) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val primarySize = if (largePrimary && maxWidth >= 192.dp) 64.dp else 48.dp
        val stacked = maxWidth < 144.dp
        val previous: @Composable () -> Unit = {
            if (session.actions.previous) MediaButton(MediaIcons.Previous, stringResource(R.string.media_previous), enabled,
                tag = "media_previous") { if (enabled) onCommand(MediaCommand.PREVIOUS, null) }
            else if (!stacked) Spacer(Modifier.size(48.dp))
        }
        val next: @Composable () -> Unit = {
            if (session.actions.next) MediaButton(MediaIcons.Next, stringResource(R.string.media_next), enabled,
                tag = "media_next") { if (enabled) onCommand(MediaCommand.NEXT, null) }
            else if (!stacked) Spacer(Modifier.size(48.dp))
        }
        val main: @Composable () -> Unit = {
            if (primary != null) {
                val label = when (primary) {
                    MediaCommand.PLAY -> R.string.media_play
                    MediaCommand.PAUSE -> R.string.media_pause
                    else -> R.string.media_toggle
                }
                MediaButton(if (session.playback.phase.active) MediaIcons.Pause else MediaIcons.Play,
                    stringResource(label), enabled, size = primarySize, primary = true, tag = "media_play_pause") {
                    if (enabled) onCommand(primary, null)
                }
            } else if (!stacked) Spacer(Modifier.size(primarySize))
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { previous(); main(); next() }
        } else {
            Row(Modifier.widthIn(max = 256.dp).fillMaxWidth().align(Alignment.Center),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { previous(); main(); next() }
        }
    }
}

@Composable
private fun MediaProgress(session: MediaSessionSnapshot, enabled: Boolean, onCommand: (MediaCommand, Long?) -> Unit, hitHeight: Dp) {
    var now by remember(session.sessionId, session.playback) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(session.sessionId, session.playback) {
        while (session.playback.phase.advances) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    var drag by remember(session.sessionId, session.metadataRevision, session.durationMs, enabled) { mutableStateOf<Float?>(null) }
    val position = session.playback.positionAt(now, session.durationMs)
    val fraction = if (position != null && session.durationMs > 0) (position.toDouble() / session.durationMs).toFloat().coerceIn(0f, 1f) else 0f
    val shownPosition = drag?.let { (it.toDouble() * session.durationMs).toLong() } ?: position
    val currentLabel = shownPosition?.let(::formatMediaTime) ?: stringResource(R.string.media_unknown_time)
    val durationLabel = if (session.durationMs > 0) formatMediaTime(session.durationMs) else stringResource(R.string.media_unknown_time)
    val seekLabel = stringResource(R.string.media_seek)
    val timeDescription = stringResource(R.string.media_progress_description, currentLabel, durationLabel)
    val timeStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()
    val labelWidth = with(LocalDensity.current) {
        (textMeasurer.measure(currentLabel, timeStyle, maxLines = 1).size.width +
            textMeasurer.measure(durationLabel, timeStyle, maxLines = 1).size.width).toDp()
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val progressWidth = maxWidth
        val progress: @Composable (Modifier) -> Unit = { trackModifier ->
            if (session.actions.seek && session.durationMs > 0 && position != null) {
                // Replacing a track cancels any in-flight gesture against the previous duration.
                key(session.sessionId, session.metadataRevision, session.durationMs) {
                    MediaSeekBar(value = fraction, enabled = enabled, description = "$seekLabel, $timeDescription",
                        onSeek = { if (enabled) onCommand(MediaCommand.SEEK, (it.toDouble() * session.durationMs).toLong()) },
                        onSeeking = { drag = it }, modifier = trackModifier.height(hitHeight))
                }
            } else {
                Box(trackModifier.height(hitHeight), contentAlignment = Alignment.Center) {
                    LinearProgressIndicator(progress = { fraction }, color = Color.White.copy(alpha = 0.85f), trackColor = Color.White.copy(alpha = 0.20f),
                        modifier = Modifier.fillMaxWidth().height(4.dp).semantics { contentDescription = timeDescription })
                }
            }
        }
        val elapsed: @Composable () -> Unit = {
            Text(currentLabel, color = MediaMuted, style = timeStyle, maxLines = 1,
                modifier = Modifier.testTag("media_elapsed"))
        }
        val duration: @Composable () -> Unit = {
            Text(durationLabel, color = MediaMuted, style = timeStyle, maxLines = 1,
                modifier = Modifier.testTag("media_duration"))
        }
        if (progressWidth >= labelWidth + 16.dp + 48.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                elapsed(); progress(Modifier.weight(1f)); duration()
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                progress(Modifier.fillMaxWidth())
                if (progressWidth >= labelWidth + 8.dp) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { elapsed(); duration() }
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        Box(Modifier.align(Alignment.Start)) { elapsed() }
                        Box(Modifier.align(Alignment.End)) { duration() }
                    }
                }
            }
        }
    }
}
private fun formatMediaTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

@Composable
private fun MediaButton(icon: ImageVector, label: String, enabled: Boolean, size: Dp = 48.dp,
    primary: Boolean = false, tag: String, onClick: () -> Unit) {
    val material = if (primary) Modifier.background(Color.White.copy(alpha = if (enabled) 0.20f else 0.08f), CircleShape)
        .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape) else Modifier
    IconButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.size(size).then(material).testTag(tag).semantics { contentDescription = label }) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = if (enabled) 0.96f else 0.38f),
            modifier = Modifier.size(if (primary) size * 0.56f else 28.dp))
    }
}

/** Native vectors preserve labelled 48 dp controls without an extra icon library. */
private object MediaIcons {
    private fun build(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply { path(fill = SolidColor(Color.Black), pathBuilder = block) }.build()
    val Play = build("MediaPlay") { moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close() }
    val Pause = build("MediaPause") {
        moveTo(6f, 5f); lineTo(10f, 5f); lineTo(10f, 19f); lineTo(6f, 19f); close()
        moveTo(14f, 5f); lineTo(18f, 5f); lineTo(18f, 19f); lineTo(14f, 19f); close()
    }
    val Previous = build("MediaPrevious") {
        moveTo(5f, 5f); lineTo(8f, 5f); lineTo(8f, 19f); lineTo(5f, 19f); close()
        moveTo(19f, 5f); lineTo(9f, 12f); lineTo(19f, 19f); close()
    }
    val Next = build("MediaNext") {
        moveTo(16f, 5f); lineTo(19f, 5f); lineTo(19f, 19f); lineTo(16f, 19f); close()
        moveTo(5f, 5f); lineTo(15f, 12f); lineTo(5f, 19f); close()
    }
}
