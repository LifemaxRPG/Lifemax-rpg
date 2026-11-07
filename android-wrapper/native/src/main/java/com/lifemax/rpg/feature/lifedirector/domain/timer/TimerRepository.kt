package com.lifemax.rpg.feature.lifedirector.domain.timer

/** Serialized timer/outbox transaction boundary. Not wired to production authority yet. */
class TimerRepository(private val journal: Journal, initialEpoch: String) {
    interface Journal {
        /** Missing is distinct from unreadable: implementations must throw on corruption. */
        fun read(): Snapshot?

        /** Atomically replace the complete snapshot, durably, or throw. */
        fun write(snapshot: Snapshot)
    }

    data class Request(
        val epoch: String,
        val sequence: Long,
        val atMs: Long,
        val command: TimerEngine.Command,
    )

    data class Pending(val id: String, val epoch: String, val completion: TimerEngine.Completion)

    data class Receipt(val request: Request, val status: TimerEngine.Status)

    data class Snapshot(
        val epoch: String,
        val lastSequence: Long = 0,
        val lastGeneration: Long = 0,
        val timer: TimerEngine.Timer? = null,
        val outbox: List<Pending> = emptyList(),
        val receipts: List<Receipt> = emptyList(),
    ) {
        init {
            require(epoch.isNotBlank())
            require(lastSequence >= 0)
            require(lastGeneration in 0 until TimerEngine.MAX_TIME)
            require(timer == null || timer.generation <= lastGeneration)
            require(outbox.size <= OUTBOX_LIMIT && receipts.size <= RECEIPT_LIMIT)
            require(
                outbox.all { it.epoch == epoch } &&
                    outbox.map { it.id }.distinct().size == outbox.size
            )
            require(
                receipts.all { it.request.epoch == epoch && it.request.sequence in 1..lastSequence }
            )
        }
    }

    enum class Delivery {
        COMMITTED,
        REPLAYED,
        WRONG_EPOCH,
        ID_CONFLICT,
        EXPIRED,
        OUT_OF_ORDER,
        OUTBOX_FULL,
        STORAGE_UNKNOWN,
    }

    data class Result(val delivery: Delivery, val status: TimerEngine.Status? = null)

    private var state: Snapshot = journal.read() ?: Snapshot(initialEpoch).also(journal::write)
    private var uncertain = false
    private var possibleEpochs = setOf(state.epoch)

    init {
        require(state.epoch == initialEpoch) { "Explicit epoch migration required" }
    }

    /** Projections are only available after a durable commit or a successful journal reread. */
    @Synchronized
    fun snapshot(): Snapshot {
        check(!uncertain) { "Reconcile storage before reading the timer" }
        return state.copy(outbox = state.outbox.toList(), receipts = state.receipts.toList())
    }

    @Synchronized
    fun reconcile() {
        val recovered = requireNotNull(journal.read()) { "Timer journal disappeared" }
        check(recovered.epoch in possibleEpochs) { "Unexpected timer epoch change" }
        state = recovered
        possibleEpochs = setOf(recovered.epoch)
        uncertain = false
    }

    @Synchronized
    fun dispatch(request: Request): Result {
        if (uncertain) return Result(Delivery.STORAGE_UNKNOWN)
        if (request.epoch != state.epoch) return Result(Delivery.WRONG_EPOCH)
        state.receipts
            .find { it.request.sequence == request.sequence }
            ?.let {
                return if (it.request == request) Result(Delivery.REPLAYED, it.status)
                else Result(Delivery.ID_CONFLICT)
            }
        if (request.sequence <= state.lastSequence) return Result(Delivery.EXPIRED)
        if (state.lastSequence == Long.MAX_VALUE || request.sequence != state.lastSequence + 1)
            return Result(Delivery.OUT_OF_ORDER)
        // Keep generations unique even after stop/start in the same millisecond or clock rollback.
        val at =
            when (request.command) {
                is TimerEngine.Command.Start,
                is TimerEngine.Command.Replace,
                is TimerEngine.Command.Reset -> maxOf(request.atMs, state.lastGeneration + 1)
                else -> request.atMs
            }
        val transition = TimerEngine.reduce(state.timer, request.command, at)
        if (transition.completion != null && state.outbox.size == OUTBOX_LIMIT)
            return Result(Delivery.OUTBOX_FULL)
        val pending =
            transition.completion?.let {
                Pending("${state.epoch}/${request.sequence}", state.epoch, it)
            }
        val next =
            state.copy(
                lastSequence = request.sequence,
                lastGeneration = maxOf(state.lastGeneration, transition.timer?.generation ?: 0),
                timer = transition.timer,
                outbox = state.outbox + listOfNotNull(pending),
                receipts =
                    (state.receipts + Receipt(request, transition.status)).takeLast(RECEIPT_LIMIT),
            )
        return if (commit(next)) Result(Delivery.COMMITTED, transition.status)
        else Result(Delivery.STORAGE_UNKNOWN)
    }

    /** Call only for a durable receiver receipt, never for a timeout or a UI success message. */
    @Synchronized
    fun acknowledge(epoch: String, completionId: String): Boolean {
        if (uncertain || epoch != state.epoch) return false
        if (state.outbox.none { it.id == completionId }) return true
        return commit(state.copy(outbox = state.outbox.filterNot { it.id == completionId }))
    }

    /** Restore/reset barrier: pending rewards must be reconciled before replacing the epoch. */
    @Synchronized
    fun rotateEpoch(expectedEpoch: String, replacement: Snapshot): Boolean {
        if (uncertain || state.epoch != expectedEpoch || state.outbox.isNotEmpty()) return false
        require(replacement.epoch != expectedEpoch)
        require(
            replacement.lastSequence == 0L &&
                replacement.outbox.isEmpty() &&
                replacement.receipts.isEmpty()
        )
        return commit(replacement)
    }

    private fun commit(next: Snapshot): Boolean {
        possibleEpochs = setOf(state.epoch, next.epoch)
        return try {
            journal.write(next)
            state = next
            possibleEpochs = setOf(next.epoch)
            true
        } catch (_: Exception) {
            // A failure may have occurred after the durable write. Do not guess or retry a new ID.
            uncertain = true
            false
        }
    }

    companion object {
        const val RECEIPT_LIMIT = 64
        const val OUTBOX_LIMIT = 256
    }
}
