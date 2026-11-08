package com.linzin.linzindeskclock.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.linzin.linzindeskclock.R
import com.linzin.linzindeskclock.media.AlbumArtBackground
import com.linzin.linzindeskclock.media.AlbumArtGlassBackground
import com.linzin.linzindeskclock.media.MediaAccess
import com.linzin.linzindeskclock.media.MediaCommand
import com.linzin.linzindeskclock.media.MediaNotificationAccess
import com.linzin.linzindeskclock.media.MediaSessionRepository
import com.linzin.linzindeskclock.media.MediaSessionSnapshot
import com.linzin.linzindeskclock.media.MediaWidgetState
import com.linzin.linzindeskclock.media.PlaybackPhase
import com.linzin.linzindeskclock.media.mediaPresentationPolicy
import com.linzin.linzindeskclock.media.primaryCommand
import kotlinx.coroutines.delay
import java.util.Locale

private val MediaSurface = Color(0xFF1E1E22)
private val MediaMuted = Color.White.copy(alpha = 0.72f)
private val MediaAccent = Color(0xFF81C7D4)

/**
 * Parent owns actual keyguard/dream privacy and UI Lock. Privacy removes sensitive content
 * immediately, outside both artwork crossfades. Keyguard alone does not block authorised
 * transport, but notification settings are always restricted to an unlocked device and UI.
 */
@Composable
fun MediaWidget(
    isLocked: Boolean,
    hideWhenLocked: Boolean,
    modifier: Modifier = Modifier,
    controlsEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val owner = LocalLifecycleOwner.current
    val repository = remember(appContext) { MediaSessionRepository(appContext) }
    val policy = mediaPresentationPolicy(isLocked, hideWhenLocked, controlsEnabled)
    val privacyHidden = policy.hideContent
    val interactionsAllowed by rememberUpdatedState(policy.allowControls)
    val settingsAllowed by rememberUpdatedState(!isLocked && controlsEnabled)
    DisposableEffect(repository, owner, privacyHidden) {
        fun update() {
            if (!privacyHidden && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                repository.setInteractionLocked(!interactionsAllowed)
                repository.start()
            } else repository.stop()
        }
        val observer = LifecycleEventObserver { _, event ->
            // start() is idempotent. A settings overlay may only pause this Activity, so
            // explicitly rebind on resume instead of waiting for the periodic access check.
            if (event == Lifecycle.Event.ON_RESUME) repository.stop()
            update()
        }
        owner.lifecycle.addObserver(observer)
        update()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            repository.stop()
        }
    }
    SideEffect { repository.setInteractionLocked(!interactionsAllowed) }
    val state by repository.state.collectAsState()
    MediaWidgetContent(
        state = state,
        isLocked = isLocked,
        hideWhenLocked = hideWhenLocked,
        controlsEnabled = controlsEnabled,
        modifier = modifier,
        onOpenNotificationAccess = {
            // openSettings also checks the live KeyguardManager, covering a lock race.
            if (settingsAllowed) MediaNotificationAccess.openSettings(context)
        },
        onCommand = { action, position ->
            state.session?.let { session ->
                if (interactionsAllowed) repository.dispatch(session.sessionId, action, position,
                    if (action == MediaCommand.SEEK) session.metadataRevision else null)
            }
        },
    )
}

/** State-only presentation, shared by the real repository host and instrumentation fixtures. */
@Composable
fun MediaWidgetContent(
    state: MediaWidgetState,
    isLocked: Boolean,
    hideWhenLocked: Boolean,
    onOpenNotificationAccess: () -> Unit,
    onCommand: (MediaCommand, Long?) -> Unit,
    modifier: Modifier = Modifier,
    controlsEnabled: Boolean = true,
) {
    val policy = mediaPresentationPolicy(isLocked, hideWhenLocked, controlsEnabled)
    Surface(modifier = modifier.testTag("media_widget"), color = MediaSurface, shape = RoundedCornerShape(20.dp)) {
        val session = state.session
        when {
            // Permission guidance is not media metadata; a locked user sees an unlock hint.
            state.access == MediaAccess.NOT_GRANTED -> MediaPermissionStatus(isLocked, controlsEnabled, onOpenNotificationAccess)
            policy.hideContent -> MediaStatus(stringResource(R.string.media_hidden))
            state.access == MediaAccess.CONNECTING -> MediaStatus(stringResource(R.string.media_connecting))
            state.access == MediaAccess.UNAVAILABLE -> MediaStatus(stringResource(R.string.media_unavailable))
            state.access != MediaAccess.READY -> MediaStatus(stringResource(R.string.media_starting))
            session == null -> MediaStatus(stringResource(R.string.media_no_session), stringResource(R.string.media_no_session_hint))
            else -> MediaPresentation(session, policy.allowControls, onCommand)
        }
    }
}

@Composable
private fun MediaPermissionStatus(isLocked: Boolean, controlsEnabled: Boolean, onOpenSettings: () -> Unit) {
    val hint = when {
        isLocked -> R.string.media_permission_unlock_hint
        !controlsEnabled -> R.string.media_permission_ui_unlock_hint
        else -> R.string.media_permission_settings_hint
    }
    MediaStatus(stringResource(R.string.media_permission_required), stringResource(hint)) {
        if (!isLocked && controlsEnabled) {
            Button(
                onClick = { if (!isLocked && controlsEnabled) onOpenSettings() },
                modifier = Modifier.padding(top = 16.dp).widthIn(max = 280.dp).fillMaxWidth()
                    .heightIn(min = 48.dp).testTag("media_permission_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MediaAccent, contentColor = MediaSurface),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text(stringResource(R.string.media_access_open_settings), textAlign = TextAlign.Center) }
        }
    }
}

@Composable
private fun MediaStatus(title: String, detail: String? = null, action: @Composable () -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val padding = if (maxWidth < 160.dp) 8.dp else 20.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (detail != null) Text(detail, color = MediaMuted, style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            action()
        }
    }
}

@Composable
private fun MediaPresentation(session: MediaSessionSnapshot, controlsEnabled: Boolean, onCommand: (MediaCommand, Long?) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        if (maxWidth >= 360.dp && maxWidth > maxHeight * 1.25f) {
            // A short landscape panel needs a full-height cover and independently scrollable controls.
            val controlsWidth = (maxWidth * 0.42f).coerceAtLeast(184.dp).coerceAtMost(maxWidth * 0.55f)
            Row(Modifier.fillMaxSize()) {
                AlbumArtBackground(session.artwork, Modifier.weight(1f).fillMaxHeight().testTag("media_artwork"))
                MediaGlassPanel(session, controlsEnabled, onCommand, Modifier.width(controlsWidth).fillMaxHeight())
            }
        } else {
            val stackedTransport = maxWidth < 160.dp
            val minimum = (if (stackedTransport) 288.dp else 184.dp) * fontScale
            val panelHeight = maxOf(maxHeight * 0.30f, minimum)
                .coerceAtMost(maxHeight * if (stackedTransport) 0.62f else 0.58f)
            Column(Modifier.fillMaxSize()) {
                // Reserve a separate viewport: the whole sharp cover is never covered by controls.
                AlbumArtBackground(session.artwork, Modifier.weight(1f).fillMaxWidth().testTag("media_artwork"))
                MediaGlassPanel(session, controlsEnabled, onCommand, Modifier.fillMaxWidth().height(panelHeight))
            }
        }
    }
}

@Composable
private fun MediaGlassPanel(session: MediaSessionSnapshot, controlsEnabled: Boolean,
    onCommand: (MediaCommand, Long?) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Box(modifier.clip(shape).background(MediaSurface).testTag("media_glass_panel")) {
        // Only this background copy is blurred. Text, seek bar and buttons stay sharp.
        AlbumArtGlassBackground(session.artwork, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(MediaSurface.copy(alpha = 0.30f))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.03f))))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape))
        MediaDetails(session, controlsEnabled, onCommand, Modifier.fillMaxSize())
    }
}

@Composable
private fun MediaDetails(session: MediaSessionSnapshot, controlsEnabled: Boolean,
    onCommand: (MediaCommand, Long?) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val compact = maxWidth < 240.dp
        val padding = if (compact) 8.dp else 16.dp
        val shadow = with(LocalDensity.current) { Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1.dp.toPx()), 4.dp.toPx()) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = padding, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
            Text(session.title.ifBlank { stringResource(R.string.media_unknown_title) }, color = Color.White,
                style = (if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge).copy(shadow = shadow),
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().testTag("media_title"))
            Text(session.artist.ifBlank { stringResource(R.string.media_unknown_artist) }, color = MediaMuted,
                style = MaterialTheme.typography.bodyMedium.copy(shadow = shadow), textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().testTag("media_artist"))
            // Routine playing/paused metadata is conveyed by the main control. Keep exceptional states visible.
            val status = when (session.playback.phase) {
                PlaybackPhase.BUFFERING, PlaybackPhase.SKIPPING -> R.string.media_state_buffering
                PlaybackPhase.CONNECTING -> R.string.media_state_connecting
                PlaybackPhase.ERROR -> R.string.media_state_error
                else -> null
            }
            if (status != null) Text(stringResource(status), color = MediaMuted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            MediaProgress(session, controlsEnabled, onCommand)
            MediaTransport(session, controlsEnabled, onCommand)
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
private fun MediaTransport(session: MediaSessionSnapshot, enabled: Boolean, onCommand: (MediaCommand, Long?) -> Unit) {
    val primary = primaryCommand(session.playback.phase, session.actions)
    if (primary == null && !session.actions.previous && !session.actions.next) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val primarySize = if (maxWidth >= 192.dp) 64.dp else 48.dp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaProgress(session: MediaSessionSnapshot, enabled: Boolean, onCommand: (MediaCommand, Long?) -> Unit) {
    var now by remember(session.sessionId, session.playback) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(session.sessionId, session.playback) {
        while (session.playback.phase.advances) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    var drag by remember(session.sessionId, session.metadataRevision, enabled) { mutableStateOf<Float?>(null) }
    val position = session.playback.positionAt(now, session.durationMs)
    val fraction = if (position != null && session.durationMs > 0) (position.toDouble() / session.durationMs).toFloat().coerceIn(0f, 1f) else 0f
    val shownPosition = drag?.let { (it.toDouble() * session.durationMs).toLong() } ?: position
    val currentLabel = shownPosition?.let(::formatMediaTime) ?: stringResource(R.string.media_unknown_time)
    val durationLabel = if (session.durationMs > 0) formatMediaTime(session.durationMs) else stringResource(R.string.media_unknown_time)
    val seekLabel = stringResource(R.string.media_seek)
    val timeDescription = stringResource(R.string.media_progress_description, currentLabel, durationLabel)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val progressWidth = maxWidth
        val progress: @Composable (Modifier) -> Unit = { trackModifier ->
            if (session.actions.seek && session.durationMs > 0 && position != null) {
                Slider(value = drag ?: fraction, onValueChange = { drag = it }, enabled = enabled,
                    onValueChangeFinished = {
                        drag?.let { if (enabled) onCommand(MediaCommand.SEEK, (it.toDouble() * session.durationMs).toLong()) }
                        drag = null
                    },
                    thumb = { Box(Modifier.size(8.dp).background(Color.White.copy(alpha = if (enabled) 1f else 0.38f), CircleShape)) },
                    track = {
                        Canvas(Modifier.fillMaxWidth().height(4.dp).testTag("media_progress_track")) {
                            val start = Offset(0f, size.height / 2)
                            drawLine(Color.White.copy(alpha = 0.20f), start, Offset(size.width, start.y), size.height, StrokeCap.Round)
                            drawLine(Color.White.copy(alpha = if (enabled) 0.95f else 0.38f), start,
                                Offset(size.width * (drag ?: fraction), start.y), size.height, StrokeCap.Round)
                        }
                    },
                    modifier = trackModifier.height(48.dp).testTag("media_seek").semantics { contentDescription = "$seekLabel, $timeDescription" })
            } else {
                Box(trackModifier.height(48.dp), contentAlignment = Alignment.Center) {
                    LinearProgressIndicator(progress = { fraction }, color = Color.White.copy(alpha = 0.85f), trackColor = Color.White.copy(alpha = 0.20f),
                        modifier = Modifier.fillMaxWidth().height(4.dp).semantics { contentDescription = timeDescription })
                }
            }
        }
        val elapsed: @Composable () -> Unit = {
            Text(currentLabel, color = MediaMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                modifier = Modifier.testTag("media_elapsed"))
        }
        val duration: @Composable () -> Unit = {
            Text(durationLabel, color = MediaMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                modifier = Modifier.testTag("media_duration"))
        }
        if (progressWidth >= 224.dp && LocalDensity.current.fontScale <= 1.3f) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                elapsed(); progress(Modifier.weight(1f)); duration()
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                progress(Modifier.fillMaxWidth())
                if (progressWidth >= 112.dp * LocalDensity.current.fontScale) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { elapsed(); duration() }
                } else {
                    // Even two short times can collide in a 20% panel at a large font scale.
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
