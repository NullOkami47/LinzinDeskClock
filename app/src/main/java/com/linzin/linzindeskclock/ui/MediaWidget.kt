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
import com.linzin.linzindeskclock.media.primaryCommand
import kotlinx.coroutines.delay
import java.util.Locale

private val MediaSurface = Color(0xFF1E1E22)
private val MediaMuted = Color.White.copy(alpha = 0.72f)
private val MediaAccent = Color(0xFF81C7D4)

/**
 * Keyguard keeps full playback information and authorised media controls available.
 * Only the explicit UI Lock blocks transports; permission setup still requires unlocking.
 */
@Composable
fun MediaWidget(
    isLocked: Boolean,
    modifier: Modifier = Modifier,
    controlsEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val owner = LocalLifecycleOwner.current
    val repository = remember(appContext) { MediaSessionRepository(appContext) }
    val interactionsAllowed by rememberUpdatedState(controlsEnabled)
    val settingsAllowed by rememberUpdatedState(!isLocked && controlsEnabled)
    DisposableEffect(repository, owner) {
        fun update() {
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
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
    onOpenNotificationAccess: () -> Unit,
    onCommand: (MediaCommand, Long?) -> Unit,
    modifier: Modifier = Modifier,
    controlsEnabled: Boolean = true,
) {
    Surface(modifier = modifier.testTag("media_widget"), color = MediaSurface, shape = RoundedCornerShape(20.dp)) {
        val session = state.session
        when {
            // Permission guidance is not media metadata; a locked user sees an unlock hint.
            state.access == MediaAccess.NOT_GRANTED -> MediaPermissionStatus(isLocked, controlsEnabled, onOpenNotificationAccess)
            state.access == MediaAccess.CONNECTING -> MediaStatus(stringResource(R.string.media_connecting))
            state.access == MediaAccess.UNAVAILABLE -> MediaStatus(stringResource(R.string.media_unavailable))
            state.access != MediaAccess.READY -> MediaStatus(stringResource(R.string.media_starting))
            session == null -> MediaStatus(stringResource(R.string.media_no_session), stringResource(R.string.media_no_session_hint))
            else -> MediaPresentation(session, controlsEnabled, onCommand)
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
