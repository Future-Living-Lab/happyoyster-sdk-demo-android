package cn.happyoyster.opensdk.demo.app

import cn.happyoyster.opensdk.TravelStatusValue
import cn.happyoyster.opensdk.demo.app.DemoTravelTransition.Operation
import org.junit.Assert.*
import org.junit.Test

class DemoTravelTransitionTest {
    @Test fun pauseAcceptanceKeepsControlsLockedUntilPausedCallback() {
        val pending = DemoTravelTransition().begin(Operation.Pause)
        val accepted = pending.accepted(pending.epoch)
        assertEquals(Operation.Pause, accepted.operation)
        assertFalse(accepted.canStart(Operation.Pause, TravelStatusValue.Running))
        assertFalse(accepted.canStart(Operation.Resume, TravelStatusValue.Paused))
        assertEquals(accepted, accepted.confirmed(TravelStatusValue.Running))
        assertTrue(accepted.confirmed(TravelStatusValue.Paused).canStart(Operation.Resume, TravelStatusValue.Paused))
    }

    @Test fun bothRecoveryActionsWaitForRunningAndKeepRewindTarget() {
        listOf(Operation.Resume, Operation.Rewind).forEach { operation ->
            val pending = DemoTravelTransition().begin(operation, 8.0)
            val accepted = pending.accepted(pending.epoch)
            assertFalse(accepted.canStart(Operation.Resume, TravelStatusValue.Paused))
            assertFalse(accepted.canStart(Operation.Rewind, TravelStatusValue.Paused))
            assertEquals(accepted, accepted.confirmed(TravelStatusValue.Paused))
            assertNull(accepted.confirmed(TravelStatusValue.Running).operation)
            assertEquals(8.0, accepted.rewindToSec!!, 0.0)
        }
    }

    @Test fun uncertaintyDoesNotOfferRetryBeforeSdkConfirmation() {
        val pending = DemoTravelTransition().begin(Operation.Rewind, 12.0)
        val uncertain = pending.failed(pending.epoch)
        assertTrue(uncertain.uncertain)
        assertFalse(uncertain.canStart(Operation.Rewind, TravelStatusValue.Paused))
        assertFalse(uncertain.canStart(Operation.Resume, TravelStatusValue.Paused))
        assertTrue(uncertain.confirmed(TravelStatusValue.Running).canStart(Operation.Pause, TravelStatusValue.Running))
    }

    @Test fun confirmationOrTerminationPreventsLateFailureFromRevivingTransition() {
        val pending = DemoTravelTransition().begin(Operation.Pause)
        listOf(TravelStatusValue.Paused, TravelStatusValue.Completed, TravelStatusValue.Failed).forEach { status ->
            val confirmed = pending.confirmed(status)
            assertEquals(confirmed, confirmed.failed(pending.epoch))
            assertEquals(confirmed, confirmed.accepted(pending.epoch))
            assertNull(confirmed.operation)
        }
        val replacement = pending.clear().begin(Operation.Resume)
        assertEquals(replacement, replacement.failed(pending.epoch))
    }
}
