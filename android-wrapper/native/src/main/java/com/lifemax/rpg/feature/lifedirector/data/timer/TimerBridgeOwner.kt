package com.lifemax.rpg.feature.lifedirector.data.timer

import com.lifemax.rpg.feature.lifedirector.domain.timer.TimerRepository
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import org.json.JSONObject

/** One plugin-lifetime journal owner. All disk and protocol work runs on one IO thread. */
class TimerBridgeOwner(
    private val file: Path,
    private val worker: java.util.concurrent.ExecutorService = Executors.newSingleThreadExecutor(),
) : AutoCloseable {
    private var journal: FileTimerJournal? = null
    private var endpoint: TimerEndpoint? = null
    @Volatile private var closed = false

    @Synchronized
    fun submit(request: JSONObject, answer: (JSONObject) -> Unit) {
        if (closed) return
        // Snapshot before crossing threads; callers cannot mutate an enqueued operation.
        val copied = JSONObject(request.toString())
        worker.execute {
            if (closed) return@execute
            val result =
                try {
                    if (!Files.exists(file)) response("NOT_PREPARED")
                    else {
                        if (endpoint == null) {
                            val opened = FileTimerJournal(file)
                            try {
                                val stored = requireNotNull(opened.read())
                                val repository = TimerRepository(opened, stored.epoch)
                                endpoint =
                                    TimerEndpoint(
                                        repository,
                                        authorityReady = { false },
                                        target = { null },
                                    )
                                journal = opened
                            } catch (e: Exception) {
                                opened.close()
                                throw e
                            }
                        }
                        endpoint!!.handle(copied)
                    }
                } catch (_: Exception) {
                    response("RECONCILE_REQUIRED")
                }
            if (!closed) answer(result)
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        // Cleanup follows any in-flight journal operation on the same thread.
        worker.execute {
            journal?.close()
            journal = null
            endpoint = null
        }
        worker.shutdown()
    }

    private fun response(status: String) = JSONObject().put("protocol", 1).put("status", status)
}
