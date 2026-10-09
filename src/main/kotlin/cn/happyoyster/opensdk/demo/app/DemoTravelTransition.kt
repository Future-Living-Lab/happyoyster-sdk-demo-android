package cn.happyoyster.opensdk.demo.app

import cn.happyoyster.opensdk.TravelStatusValue

/** Host intent stays pending until the SDK confirms media state, even after HTTP acceptance. */
internal data class DemoTravelTransition(
    val epoch: Long = 0,
    val operation: Operation? = null,
    val requestInFlight: Boolean = false,
    val uncertain: Boolean = false,
    val rewindToSec: Double? = null,
) {
    enum class Operation { Pause, Resume, Rewind }

    fun begin(next: Operation, rewindSeconds: Double? = null) =
        DemoTravelTransition(epoch + 1, next, requestInFlight = true, rewindToSec = rewindSeconds)

    fun accepted(attempt: Long) =
        if (attempt == epoch && operation != null) copy(requestInFlight = false) else this

    fun failed(attempt: Long) =
        if (attempt == epoch && operation != null) copy(requestInFlight = false, uncertain = true) else this

    fun clear() = DemoTravelTransition(epoch + 1)

    fun confirmed(status: TravelStatusValue): DemoTravelTransition = when {
        status == TravelStatusValue.Completed || status == TravelStatusValue.Failed -> clear()
        operation == Operation.Pause && status == TravelStatusValue.Paused -> clear()
        operation in setOf(Operation.Resume, Operation.Rewind) && status == TravelStatusValue.Running -> clear()
        else -> this
    }

    fun canStart(next: Operation, status: TravelStatusValue?): Boolean {
        if (operation != null) return false
        return when (next) {
            Operation.Pause -> status == TravelStatusValue.Running
            Operation.Resume, Operation.Rewind -> status == TravelStatusValue.Paused
        }
    }
}
