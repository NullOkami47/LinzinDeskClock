package com.linzin.linzindeskclock.media

import org.junit.Assert.*
import org.junit.Test

class MediaLogicTest {
    @Test fun playingPreemptsPausedEvenWhenPausedWasSelected() {
        val sessions = listOf(SessionCandidate("paused", PlaybackPhase.PAUSED), SessionCandidate("playing", PlaybackPhase.PLAYING))
        assertEquals("playing", selectSession(sessions, "paused"))
    }

    @Test fun equalPriorityKeepsSelectionDespiteManagerReordering() {
        val sessions = listOf(SessionCandidate("new", PlaybackPhase.PLAYING), SessionCandidate("current", PlaybackPhase.PLAYING))
        assertEquals("current", selectSession(sessions, "current"))
        assertEquals("new", selectSession(sessions, "destroyed"))
        assertNull(selectSession(emptyList<SessionCandidate<String>>(), "current"))
    }

    @Test fun pausedSessionWinsOverIdleAndUnknownSessions() {
        val sessions = listOf(SessionCandidate("idle", PlaybackPhase.STOPPED), SessionCandidate("unknown", PlaybackPhase.NONE), SessionCandidate("paused", PlaybackPhase.PAUSED))
        assertEquals("paused", selectSession(sessions, "idle"))
    }

    @Test fun directTransportIsNeverInventedFromToggleSupport() {
        val actions = SupportedActions(toggle = true)
        assertEquals(MediaCommand.TOGGLE, primaryCommand(PlaybackPhase.PAUSED, actions))
        assertEquals(MediaCommand.TOGGLE, primaryCommand(PlaybackPhase.PLAYING, actions))
        assertFalse(isCommandSupported(MediaCommand.PLAY, PlaybackPhase.PAUSED, actions, 0))
        assertFalse(isCommandSupported(MediaCommand.PAUSE, PlaybackPhase.PLAYING, actions, 0))
        assertNull(primaryCommand(PlaybackPhase.BUFFERING, actions))
    }

    @Test fun playingWithOnlyPlaySupportHasNoPauseButton() {
        assertNull(primaryCommand(PlaybackPhase.PLAYING, SupportedActions(play = true)))
        assertEquals(MediaCommand.PAUSE, primaryCommand(PlaybackPhase.PLAYING, SupportedActions(pause = true, toggle = true)))
        assertNull(primaryCommand(PlaybackPhase.PAUSED, SupportedActions(pause = true)))
        assertFalse(isCommandSupported(MediaCommand.NEXT, PlaybackPhase.PLAYING, SupportedActions(previous = true), 10_000))
    }

    @Test fun seekingRequiresBothCapabilityAndKnownDuration() {
        val actions = SupportedActions(seek = true)
        assertFalse(isCommandSupported(MediaCommand.SEEK, PlaybackPhase.PLAYING, actions, 0))
        assertFalse(isCommandSupported(MediaCommand.SEEK, PlaybackPhase.PLAYING, actions, -1))
        assertTrue(isCommandSupported(MediaCommand.SEEK, PlaybackPhase.PAUSED, actions, 60_000))
        assertEquals(60_000L, clampSeekPosition(75_000, 60_000))
        assertEquals(0L, clampSeekPosition(-1, 60_000))
    }

    @Test fun controlsBlockStopRevocationAndStaleSessionAllBlockDispatch() {
        assertTrue(canControlMedia(started = true, authorised = true, connected = true, controlsBlocked = false, expectedSessionId = 7, selectedSessionId = 7))
        for (blockedFlag in 0..3) {
            assertFalse(canControlMedia(started = blockedFlag != 0, authorised = blockedFlag != 1, connected = blockedFlag != 2, controlsBlocked = blockedFlag == 3, expectedSessionId = 7, selectedSessionId = 7))
        }
        assertFalse(canControlMedia(true, true, true, false, 6, 7))
        assertFalse(canControlMedia(true, true, true, false, 7, null))
    }

    @Test fun progressUsesElapsedRealtimeAndPlaybackSpeed() {
        val clock = PlaybackClock(10_000, 1_000, 1.5f, PlaybackPhase.PLAYING)
        assertEquals(13_000L, clock.positionAt(3_000, 60_000))
        assertEquals(10_000L, clock.copy(phase = PlaybackPhase.PAUSED).positionAt(3_000, 60_000))
        assertEquals(10_000L, clock.copy(phase = PlaybackPhase.BUFFERING).positionAt(3_000, 60_000))
    }

    @Test fun progressClampsEndAndReverseAndRejectsInvalidAnchors() {
        val clock = PlaybackClock(10_000, 1_000, 2f, PlaybackPhase.PLAYING)
        assertEquals(11_000L, clock.positionAt(3_000, 11_000))
        assertEquals(0L, clock.copy(speed = -10f, phase = PlaybackPhase.REWINDING).positionAt(3_000, 60_000))
        assertEquals(10_000L, clock.positionAt(500, 60_000))
        assertEquals(10_000L, clock.copy(updatedAtElapsedMs = 0).positionAt(3_000, 60_000))
        assertEquals(10_000L, clock.copy(speed = Float.NaN).positionAt(3_000, 60_000))
        assertEquals(10_000L, clock.copy(speed = Float.POSITIVE_INFINITY).positionAt(3_000, 60_000))
    }

    @Test fun unknownPositionStaysUnknownAndUnknownDurationDoesNotInventAnEnd() {
        assertNull(PlaybackClock(-1, 1_000, 1f, PlaybackPhase.PLAYING).positionAt(2_000, 0))
        assertEquals(11_000L, PlaybackClock(10_000, 1_000, 1f, PlaybackPhase.PLAYING).positionAt(2_000, 0))
        assertEquals(Long.MAX_VALUE, PlaybackClock(Long.MAX_VALUE - 5, 1, Float.MAX_VALUE, PlaybackPhase.PLAYING).positionAt(Long.MAX_VALUE, 0))
    }
    @Test fun explicitUiLockStillBlocksTransport() {
        assertFalse(canControlMedia(true,true,true,true,7,7))
        assertTrue(canControlMedia(true,true,true,false,7,7))
    }

    @Test fun authorisationAndSessionIdentityStillProtectLockScreenTransport() {
        val actions=SupportedActions(play=true,pause=true,previous=true,next=true,seek=true)
        for((command,phase) in listOf(
            MediaCommand.PLAY to PlaybackPhase.PAUSED,
            MediaCommand.PAUSE to PlaybackPhase.PLAYING,
            MediaCommand.PREVIOUS to PlaybackPhase.PLAYING,
            MediaCommand.NEXT to PlaybackPhase.PLAYING,
            MediaCommand.SEEK to PlaybackPhase.PLAYING
        )) assertTrue(isCommandSupported(command,phase,actions,60_000))
        assertFalse(canControlMedia(true,false,true,false,7,7))
        assertFalse(canControlMedia(true,true,false,false,7,7))
        assertFalse(canControlMedia(true,true,true,false,7,8))
    }
}
