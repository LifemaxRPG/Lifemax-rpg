package com.lifemax.rpg.feature.lifedirector.domain.timer

/** One FIFO delivery attempt, called by a lifecycle-bound IO worker, never a UI ticker. */
class CompletionDelivery(
    private val repository: TimerRepository,
    private val transport: Transport,
) {
    fun interface Transport {
        /**
         * The bridge adapter must validate protocol version and decode the exact echoed identity.
         */
        fun deliver(pending: TimerRepository.Pending): Reply
    }

    enum class RemoteStatus {
        COMMITTED,
        UNKNOWN,
        BUSY,
        REJECTED,
        EPOCH_MISMATCH,
        ID_CONFLICT,
        EXPIRED,
    }

    data class Reply(val status: RemoteStatus, val identity: TimerRepository.Pending)

    enum class Outcome {
        EMPTY,
        ACKNOWLEDGED,
        RETRY,
        NEEDS_REVIEW,
        STORAGE_UNKNOWN,
    }

    @Synchronized
    fun attempt(): Outcome {
        val pending =
            try {
                repository.snapshot().outbox.firstOrNull()
            } catch (_: IllegalStateException) {
                return Outcome.STORAGE_UNKNOWN
            } ?: return Outcome.EMPTY
        val reply =
            try {
                transport.deliver(pending)
            } catch (_: Exception) {
                return Outcome.RETRY
            }
        if (reply.identity != pending) return Outcome.NEEDS_REVIEW
        return when (reply.status) {
            RemoteStatus.COMMITTED ->
                if (repository.acknowledge(pending.epoch, pending.id)) Outcome.ACKNOWLEDGED
                else Outcome.STORAGE_UNKNOWN
            RemoteStatus.UNKNOWN,
            RemoteStatus.BUSY -> Outcome.RETRY
            else -> Outcome.NEEDS_REVIEW
        }
    }
}
