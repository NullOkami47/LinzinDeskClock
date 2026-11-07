package com.linzin.linzindeskclock.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.linzin.linzindeskclock.R
import com.linzin.linzindeskclock.media.AlbumArtBackground
import com.linzin.linzindeskclock.media.MediaAccess
import com.linzin.linzindeskclock.media.MediaCommand
import com.linzin.linzindeskclock.media.MediaSessionRepository
import com.linzin.linzindeskclock.media.MediaSessionSnapshot
import com.linzin.linzindeskclock.media.PlaybackPhase
import com.linzin.linzindeskclock.media.mediaPresentationPolicy
import com.linzin.linzindeskclock.media.primaryCommand
import kotlinx.coroutines.delay
import java.util.Locale

private val MediaSurface = Color(0xFF1E1E22)
private val MediaMuted = Color(0xFFA0A0AA)
private val MediaAccent = Color(0xFF81C7D4)

/**
 * Parent owns pager/layout and actual keyguard/dream privacy state.
 * isLocked + hideWhenLocked removes all metadata/art immediately, without a fade-out.
 * With privacy off, authorised transport remains available while locked.
 * controlsEnabled=false is UI Lock, retaining visible metadata and artwork.
 * Permission/setup is deliberately absent; the parent handles that in unlocked settings.
 */
@Composable
fun MediaWidget(
    isLocked: Boolean,
    hideWhenLocked: Boolean,
    modifier: Modifier = Modifier,
    controlsEnabled: Boolean = true,
) {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val repository = remember(context) { MediaSessionRepository(context) }
    val policy = mediaPresentationPolicy(isLocked, hideWhenLocked, controlsEnabled)
    val privacyHidden = policy.hideContent
    val interactionsAllowed by rememberUpdatedState(policy.allowControls)
    DisposableEffect(repository, owner, privacyHidden) {
        fun update() {
            if (!privacyHidden && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                repository.setInteractionLocked(!interactionsAllowed)
                repository.start()
            } else repository.stop()
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        owner.lifecycle.addObserver(observer)
        update()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            repository.stop()
        }
    }
    SideEffect { repository.setInteractionLocked(!interactionsAllowed) }
    val state by repository.state.collectAsState()
    Surface(modifier = modifier, color = MediaSurface, shape = RoundedCornerShape(20.dp)) {
        // Outside the art crossfade: privacy hiding/revocation removes outgoing sensitive frames.
        val session = state.session
        when {
            privacyHidden -> MediaStatus(stringResource(R.string.media_hidden))
            state.access == MediaAccess.NOT_GRANTED -> MediaStatus(
                stringResource(R.string.media_permission_required),
                if (isLocked) null else stringResource(R.string.media_permission_settings_hint),
            )
            state.access == MediaAccess.CONNECTING -> MediaStatus(stringResource(R.string.media_connecting))
            state.access == MediaAccess.UNAVAILABLE -> MediaStatus(stringResource(R.string.media_unavailable))
            state.access != MediaAccess.READY -> MediaStatus(stringResource(R.string.media_starting))
            session == null -> MediaStatus(stringResource(R.string.media_no_session), stringResource(R.string.media_no_session_hint))
            else -> {
                val overlayFadePx = with(LocalDensity.current) { 32.dp.toPx() }
                val overlay = remember(overlayFadePx) {
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                        endY = overlayFadePx,
                    )
                }
                Box(Modifier.fillMaxSize()) {
                    // The fitted cover and mirrored margins use the entire secondary panel.
                    // Text/control layout never changes the image's fit rectangle.
                    AlbumArtBackground(session.artwork, Modifier.matchParentSize())
                    val command: (MediaCommand, Long?) -> Unit = { action, position ->
                        if (interactionsAllowed) repository.dispatch(session.sessionId, action, position,
                            if (action == MediaCommand.SEEK) session.metadataRevision else null)
                    }
                    MediaDetails(
                        session = session,
                        controlsEnabled = policy.allowControls,
                        onCommand = command,
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .background(overlay)
                            .padding(start = 12.dp, end = 12.dp, top = 32.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaStatus(title: String, detail: String? = null) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (detail != null) Text(detail, color = MediaMuted, style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun MediaDetails(
    session: MediaSessionSnapshot,
    controlsEnabled: Boolean,
    onCommand: (MediaCommand, Long?) -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Text(session.title.ifBlank { stringResource(R.string.media_unknown_title) }, color = Color.White,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(session.artist.ifBlank { stringResource(R.string.media_unknown_artist) }, color = MediaMuted,
            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (session.album.isNotBlank()) Text(session.album, color = MediaMuted,
            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stringResource(when (session.playback.phase) {
            PlaybackPhase.PLAYING, PlaybackPhase.FAST_FORWARDING, PlaybackPhase.REWINDING -> R.string.media_state_playing
            PlaybackPhase.PAUSED -> R.string.media_state_paused
            PlaybackPhase.BUFFERING, PlaybackPhase.SKIPPING -> R.string.media_state_buffering
            PlaybackPhase.CONNECTING -> R.string.media_state_connecting
            PlaybackPhase.STOPPED -> R.string.media_state_stopped
            PlaybackPhase.ERROR -> R.string.media_state_error
            PlaybackPhase.NONE -> R.string.media_state_idle
        }), color = MediaAccent, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
        MediaProgress(session, controlsEnabled, onCommand)
        val primary = primaryCommand(session.playback.phase, session.actions)
        val hasControls = primary != null || session.actions.previous || session.actions.next
        if (hasControls) BoxWithConstraints(Modifier.fillMaxWidth()) {
            val buttons: @Composable () -> Unit = {
                if (session.actions.previous) MediaButton(MediaIcons.Previous, stringResource(R.string.media_previous), controlsEnabled) { onCommand(MediaCommand.PREVIOUS, null) }
                if (primary != null) {
                    val label = when (primary) {
                        MediaCommand.PLAY -> R.string.media_play
                        MediaCommand.PAUSE -> R.string.media_pause
                        else -> R.string.media_toggle
                    }
                    MediaButton(if (session.playback.phase.active) MediaIcons.Pause else MediaIcons.Play,
                        stringResource(label), controlsEnabled) { onCommand(primary, null) }
                }
                if (session.actions.next) MediaButton(MediaIcons.Next, stringResource(R.string.media_next), controlsEnabled) { onCommand(MediaCommand.NEXT, null) }
            }
            if (maxWidth < 144.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { buttons() }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically) { buttons() }
            }
        }
        val hint = when {
            !controlsEnabled -> R.string.media_controls_ui_locked
            !hasControls && !session.actions.seek -> R.string.media_controls_unavailable
            else -> null
        }
        if (hint != null) Text(stringResource(hint), color = MediaMuted, style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
    }
}

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
    if (session.actions.seek && session.durationMs > 0 && position != null) {
        Slider(value = drag ?: fraction, onValueChange = { drag = it }, enabled = enabled,
            onValueChangeFinished = {
                drag?.let { if (enabled) onCommand(MediaCommand.SEEK, (it.toDouble() * session.durationMs).toLong()) }
                drag = null
            }, colors = SliderDefaults.colors(thumbColor = MediaAccent, activeTrackColor = MediaAccent),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = seekLabel })
    } else {
        LinearProgressIndicator(progress = { fraction }, color = MediaAccent, trackColor = Color(0xFF2C2C34),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp))
    }
    val timeDescription = stringResource(R.string.media_progress_description, currentLabel, durationLabel)
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = timeDescription }, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(currentLabel, color = MediaMuted, style = MaterialTheme.typography.labelSmall)
        Text(durationLabel, color = MediaMuted, style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatMediaTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

@Composable
private fun MediaButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp).semantics { contentDescription = label }) {
        Icon(icon, contentDescription = null, tint = if (enabled) MediaAccent else MediaMuted.copy(alpha = 0.4f), modifier = Modifier.size(28.dp))
    }
}

/** Small native vectors avoid an extra icon library and provide real labelled 48 dp controls. */
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
