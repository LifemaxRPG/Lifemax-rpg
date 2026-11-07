package com.lifemax.rpg.feature.lifedirector.data.timer

import com.lifemax.rpg.feature.lifedirector.domain.timer.TimerEngine as E
import com.lifemax.rpg.feature.lifedirector.domain.timer.TimerRepository as R
import org.json.JSONObject

/**
 * Bridge-facing controller. The composition root supplies a verified authority gate and schedule.
 */
class TimerEndpoint(
    private val repository: R,
    private val authorityReady: () -> Boolean,
    private val target: (String) -> E.Target?,
) {
    @Synchronized
    fun handle(request: JSONObject): JSONObject {
        return try {
            require(request.number("protocol") == 1L)
            val epoch = request.text("epoch")
            val snapshot = repository.snapshot()
            if (epoch != snapshot.epoch) return answer("WRONG_EPOCH")
            val action = request.text("action")
            if (action == "snapshot") return answer("OK").put("snapshot", snapshot.json())
            // Gate is provided by native composition, never accepted from a caller's payload.
            if (!authorityReady()) return answer("NOT_ACTIVE")
            val id = request.text("activityId")
            val sequence = request.number("sequence")
            val prior = snapshot.receipts.find { it.request.sequence == sequence }?.request?.command
            fun expected() = E.Key(id, request.number("generation"))
            fun resolved(): E.Target {
                val recorded =
                    when {
                        action == "start" && prior is E.Command.Start -> prior.target
                        action == "replace" && prior is E.Command.Replace -> prior.target
                        else -> null
                    }
                if (recorded?.activityId == id) return recorded
                val found = requireNotNull(target(id)) { "Activity no longer available" }
                require(found.activityId == id && found.available)
                return found
            }
            val command =
                when (action) {
                    "start" -> E.Command.Start(resolved())
                    "replace" ->
                        E.Command.Replace(
                            E.Key(request.text("previousActivityId"), request.number("generation")),
                            resolved(),
                        )
                    "pause" -> E.Command.Pause(expected())
                    "resume" -> E.Command.Resume(expected())
                    "reset" -> E.Command.Reset(expected())
                    "stop" -> E.Command.Stop(expected())
                    "finish" -> E.Command.Finish(expected())
                    "catchUp" -> E.Command.Finish(expected(), automatic = true)
                    else -> return answer("INVALID")
                }
            val operation = R.Request(epoch, sequence, request.number("atMs"), command)
            require(operation.sequence > 0 && operation.atMs in 1 until E.MAX_TIME)
            val result = repository.dispatch(operation)
            val response = answer(result.delivery.name).put("sequence", operation.sequence)
            result.status?.let { response.put("timerStatus", it.name) }
            if (result.delivery != R.Delivery.STORAGE_UNKNOWN)
                response.put("snapshot", repository.snapshot().json())
            response
        } catch (_: IllegalArgumentException) {
            answer("INVALID")
        } catch (_: org.json.JSONException) {
            answer("INVALID")
        } catch (_: IllegalStateException) {
            answer("RECONCILE_REQUIRED")
        }
    }

    private fun answer(status: String) = JSONObject().put("protocol", 1).put("status", status)

    private fun JSONObject.text(key: String): String {
        val value = get(key)
        require(value is String && value.isNotBlank() && value.length <= 512)
        return value
    }

    private fun JSONObject.number(key: String): Long {
        val value = get(key)
        require(value is Number)
        val n = value.toDouble()
        require(
            n.isFinite() && n >= 0 && n <= E.MAX_TIME.toDouble() && n == value.toLong().toDouble()
        )
        return value.toLong()
    }

    private fun R.Snapshot.json(): JSONObject =
        JSONObject()
            .put("epoch", epoch)
            .put("sequence", lastSequence)
            .put("pendingCompletions", outbox.size)
            .put(
                "timer",
                timer?.let {
                    JSONObject()
                        .put("activityId", it.activityId)
                        .put("generation", it.generation)
                        .put("plannedMs", it.plannedMs)
                        .put("pausedAtMs", it.pausedAt ?: JSONObject.NULL)
                        .put("pausedMs", it.pausedMs)
                } ?: JSONObject.NULL,
            )
}
