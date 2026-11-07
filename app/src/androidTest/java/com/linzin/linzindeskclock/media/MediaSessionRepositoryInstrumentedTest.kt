package com.linzin.linzindeskclock.media

import android.graphics.Bitmap
import android.graphics.Color
import android.media.session.PlaybackState
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaSessionRepositoryInstrumentedTest {
    @get:Rule val emulator: org.junit.rules.TestRule = DedicatedMediaEmulatorRule()

    @Test fun realMetadataArtworkCallbacksAndLifecycleRestart() = withMediaRepository {
        startReady()
        val cover = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val fixture = session("metadata-first", PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PLAY, cover)
        val first = awaitSession("metadata-first") { it.artwork != null }
        assertEquals(context.packageName, first.packageName)
        assertEquals("Fixture artist", first.artist)
        assertEquals("Fixture album", first.album)
        assertEquals(60_000L, first.durationMs)
        assertNotNull(first.artwork)
        val scaled = first.artwork!!
        assertTrue("Cover must be downsampled", maxOf(scaled.width, scaled.height) <= 1024)
        assertEquals(2f, scaled.width.toFloat() / scaled.height, 0.02f)
        assertEquals(Color.RED, scaled.getPixel(scaled.width / 2, scaled.height / 2))

        mediaOnMain { fixture.publishMetadata("metadata-second", artwork = null, durationMs = 90_000) }
        val second = awaitSession("metadata-second") { it.artwork == null && it.durationMs == 90_000L }
        assertTrue(second.metadataRevision > first.metadataRevision)
        assertEquals(first.sessionId, second.sessionId)
        mediaOnMain { fixture.clearMetadata() }
        awaitState("null metadata clears all private fields") {
            it.session?.let { value -> value.title.isEmpty() && value.artist.isEmpty() && value.album.isEmpty() && value.artwork == null && value.durationMs == 0L } == true
        }
        mediaOnMain { repository.stop(); repository.stop() }
        assertEquals(MediaWidgetState(), repository.state.value)
        assertFalse(mediaOnMain { repository.dispatch(first.sessionId, MediaCommand.PLAY) })
        mediaOnMain { fixture.publishMetadata("metadata-while-stopped") }
        assertMediaValueStays(repository.state, "stopped repository") { it == MediaWidgetState() }
        mediaOnMain { repository.setInteractionLocked(false); repository.start() }
        val restarted = awaitSession("metadata-while-stopped")
        assertNotEquals(first.sessionId, restarted.sessionId)
        assertFalse(mediaOnMain { repository.dispatch(first.sessionId, MediaCommand.PLAY) })
    }

    @Test fun supportedTransportsReachRealCallbacksAndUnsupportedActionsNeverDo() = withMediaRepository {
        startReady()
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO
        val fixture = session("controls", PlaybackState.STATE_PAUSED, actions)
        val selected = awaitSession("controls")
        assertTrue(mediaOnMain { repository.dispatch(selected.sessionId, MediaCommand.PLAY) })
        fixture.awaitEvent(1, FixtureTransport("play"))
        awaitSession("controls") { it.playback.phase == PlaybackPhase.PLAYING }
        assertTrue(mediaOnMain { repository.dispatch(selected.sessionId, MediaCommand.PAUSE) })
        fixture.awaitEvent(2, FixtureTransport("pause"))
        awaitSession("controls") { it.playback.phase == PlaybackPhase.PAUSED }
        assertTrue(mediaOnMain { repository.dispatch(selected.sessionId, MediaCommand.SEEK, 999_999) })
        fixture.awaitEvent(3, FixtureTransport("seek", 60_000))
        awaitSession("controls") { it.playback.positionMs == 60_000L }
        assertTrue(mediaOnMain { repository.dispatch(selected.sessionId, MediaCommand.SEEK, -500) })
        fixture.awaitEvent(4, FixtureTransport("seek", 0))
        awaitSession("controls") { it.playback.positionMs == 0L }
        val beforeTrackChange = repository.state.value.session!!
        assertTrue(mediaOnMain { repository.dispatch(selected.sessionId, MediaCommand.NEXT) })
        fixture.awaitEvent(5, FixtureTransport("next"))
        awaitSession("controls-next")
        assertFalse(mediaOnMain { repository.dispatch(selected.sessionId, MediaCommand.SEEK, 500, beforeTrackChange.metadataRevision) })
        assertTrue(mediaOnMain { repository.dispatch(selected.sessionId, MediaCommand.PREVIOUS) })
        fixture.awaitEvent(6, FixtureTransport("previous"))
        awaitSession("controls-previous")
        mediaOnMain { fixture.publishPlayback(supportedActions = PlaybackState.ACTION_PLAY) }
        awaitSession("controls-previous") { it.actions == SupportedActions(play = true) }
        for (command in listOf(MediaCommand.PAUSE, MediaCommand.NEXT, MediaCommand.PREVIOUS, MediaCommand.SEEK, MediaCommand.TOGGLE)) {
            assertFalse("Unsupported command $command", mediaOnMain { repository.dispatch(selected.sessionId, command, 123) })
        }
        assertFalse(mediaOnMain { repository.dispatch(selected.sessionId + 100, MediaCommand.PLAY) })
        mediaOnMain { repository.setInteractionLocked(true) }
        assertFalse(mediaOnMain { repository.dispatch(selected.sessionId, MediaCommand.PLAY) })
        assertMediaValueStays(fixture.events, "rejected transports") { it.size == 6 }
        mediaOnMain { repository.setInteractionLocked(false) }
        assertTrue(mediaOnMain { repository.dispatch(selected.sessionId, MediaCommand.PLAY) })
        fixture.awaitEvent(7, FixtureTransport("play"))
        awaitSession("controls-previous") { it.playback.phase == PlaybackPhase.PLAYING }
    }

    @Test fun toggleOnlySessionReceivesMediaButtonTransport() = withMediaRepository {
        startReady()
        val fixture = session("toggle", PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PLAY_PAUSE)
        val selected = awaitSession("toggle")
        assertEquals(SupportedActions(toggle = true), selected.actions)
        assertFalse(mediaOnMain { repository.dispatch(selected.sessionId, MediaCommand.PLAY) })
        assertTrue(mediaOnMain { repository.dispatch(selected.sessionId, MediaCommand.TOGGLE) })
        fixture.awaitEvent(1, FixtureTransport("toggle"))
        awaitSession("toggle") { it.playback.phase == PlaybackPhase.PLAYING }
        assertFalse(mediaOnMain { repository.dispatch(selected.sessionId, MediaCommand.PAUSE) })
        assertTrue(mediaOnMain { repository.dispatch(selected.sessionId, MediaCommand.TOGGLE) })
        fixture.awaitEvent(2, FixtureTransport("toggle"))
        awaitSession("toggle") { it.playback.phase == PlaybackPhase.PAUSED }
        assertMediaValueStays(fixture.events, "toggle callback count") { it.size == 2 }
    }

    @Test fun activeSessionsSelectionDestructionAndDeactivationUseRealCallbacks() = withMediaRepository {
        startReady()
        val first = session("selection-a", PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PLAY)
        val selectedA = awaitSession("selection-a")
        val second = session("selection-b", PlaybackState.STATE_PLAYING, PlaybackState.ACTION_PAUSE)
        val selectedB = awaitSession("selection-b")
        assertNotEquals(selectedA.sessionId, selectedB.sessionId)
        mediaOnMain { first.publishPlayback(PlaybackState.STATE_PLAYING, PlaybackState.ACTION_PAUSE) }
        assertMediaValueStays(repository.state, "same-priority selection") { it.session?.sessionId == selectedB.sessionId }
        mediaOnMain { second.close() }
        awaitSession("selection-a") { it.playback.phase == PlaybackPhase.PLAYING }
        assertFalse(mediaOnMain { repository.dispatch(selectedB.sessionId, MediaCommand.PAUSE) })
        mediaOnMain { first.setActive(false) }
        awaitState("deactivated session removed") { it.access == MediaAccess.READY && it.session == null }
        mediaOnMain { first.setActive(true) }
        val reactivated = awaitSession("selection-a")
        assertNotEquals(selectedA.sessionId, reactivated.sessionId)
        mediaOnMain { first.close() }
        awaitState("last session destroyed") { it.access == MediaAccess.READY && it.session == null }
        assertFalse(mediaOnMain { repository.dispatch(reactivated.sessionId, MediaCommand.PAUSE) })
    }

    @Test fun permissionRevocationClearsStateAndCoverBeforeAnyDispatchThenReauthorises() = withMediaRepository {
        startReady()
        val cover = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val fixture = session("revocation", PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PLAY, cover)
        val before = awaitSession("revocation") { it.artwork != null }
        access.setGranted(false)
        // No dispatch or restart triggers this: the actual revoke/connection observer must clear it.
        awaitState("revocation cleared metadata and artwork") { it.access == MediaAccess.NOT_GRANTED && it.session == null }
        assertFalse(mediaOnMain { repository.dispatch(before.sessionId, MediaCommand.PLAY) })
        mediaOnMain { fixture.publishMetadata("revoked-update", artwork = cover) }
        assertMediaValueStays(repository.state, "revoked repository") { it.access == MediaAccess.NOT_GRANTED && it.session == null }
        assertMediaValueStays(fixture.events, "revoked transport") { it.isEmpty() }
        access.setGranted(true)
        val after = awaitSession("revoked-update") { it.artwork != null }
        assertNotEquals(before.sessionId, after.sessionId)
        assertFalse(mediaOnMain { repository.dispatch(before.sessionId, MediaCommand.PLAY) })
        assertTrue(mediaOnMain { repository.dispatch(after.sessionId, MediaCommand.PLAY) })
        fixture.awaitEvent(1, FixtureTransport("play"))
        awaitSession("revoked-update") { it.playback.phase == PlaybackPhase.PLAYING }
    }

    @Test fun missingAccessDoesNotExposeActiveFixture() = withMediaRepository(initiallyGranted = false) {
        val fixture = session("denied", PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PLAY)
        mediaOnMain { repository.setInteractionLocked(false); repository.start() }
        awaitState("missing notification access") { it.access == MediaAccess.NOT_GRANTED && it.session == null }
        assertFalse(mediaOnMain { repository.dispatch(1, MediaCommand.PLAY) })
        assertMediaValueStays(fixture.events, "missing-access transports") { it.isEmpty() }
        access.setGranted(true)
        awaitSession("denied")
    }

    @Test fun immediateStopDiscardsPendingArtworkAndMetadataWork() = withMediaRepository {
        startReady()
        val cover = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val fixture = session("pending-artwork", PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PLAY, cover)
        awaitSession("pending-artwork")
        mediaOnMain {
            repository.stop()
            // Existing active fixture is read synchronously by start; artwork work is asynchronous.
            repository.start()
            repository.stop()
            fixture.publishMetadata("after-immediate-stop", artwork = cover)
        }
        assertEquals(MediaWidgetState(), repository.state.value)
        assertMediaValueStays(repository.state, "late metadata/artwork after stop") { it == MediaWidgetState() }
        mediaOnMain { repository.setInteractionLocked(false); repository.start() }
        awaitSession("after-immediate-stop") { it.artwork != null }
    }
}
