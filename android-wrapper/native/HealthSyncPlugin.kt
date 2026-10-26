package com.lifemax.rpg

import androidx.activity.result.ActivityResult
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId

/** Reads workouts, steps, sleep and weight from Health Connect (where Samsung Health shares them). */
@CapacitorPlugin(name = "HealthSync")
class HealthSyncPlugin : Plugin() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val all = linkedMapOf(
        "exercise" to HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        "steps" to HealthPermission.getReadPermission(StepsRecord::class),
        "sleep" to HealthPermission.getReadPermission(SleepSessionRecord::class),
        "weight" to HealthPermission.getReadPermission(WeightRecord::class)
    )

    private fun available() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun wanted(call: PluginCall): Set<String> {
        val a = call.getArray("types")
        val names = if (a == null) all.keys.toList() else (0 until a.length()).map { a.getString(it) }
        return names.mapNotNull { all[it] }.toSet()
    }

    private suspend fun grantedNames(c: HealthConnectClient): JSArray {
        val g = c.permissionController.getGrantedPermissions()
        val out = JSArray()
        for ((name, perm) in all) if (g.contains(perm)) out.put(name)
        return out
    }

    @PluginMethod
    fun status(call: PluginCall) {
        scope.launch {
            try {
                val st = HealthConnectClient.getSdkStatus(context)
                val ret = JSObject()
                ret.put("sdk", when (st) {
                    HealthConnectClient.SDK_AVAILABLE -> "available"
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "update"
                    else -> "unavailable"
                })
                ret.put("granted", if (st == HealthConnectClient.SDK_AVAILABLE) grantedNames(HealthConnectClient.getOrCreate(context)) else JSArray())
                call.resolve(ret)
            } catch (e: Throwable) {
                call.reject("status failed: $e")
            }
        }
    }

    @PluginMethod
    fun request(call: PluginCall) {
        if (!available()) { call.reject("Health Connect is not available on this phone"); return }
        val contract = PermissionController.createRequestPermissionResultContract()
        val intent = contract.createIntent(context, wanted(call))
        startActivityForResult(call, intent, "permResult")
    }

    @ActivityCallback
    private fun permResult(call: PluginCall?, result: ActivityResult) {
        if (call == null) return
        scope.launch {
            try {
                val ret = JSObject()
                ret.put("granted", grantedNames(HealthConnectClient.getOrCreate(context)))
                call.resolve(ret)
            } catch (e: Throwable) {
                call.reject("permission result failed: $e")
            }
        }
    }

    private fun range(call: PluginCall): TimeRangeFilter {
        val from = call.getLong("from") ?: (System.currentTimeMillis() - 86400000L)
        val to = call.getLong("to") ?: System.currentTimeMillis()
        return TimeRangeFilter.between(Instant.ofEpochMilli(from), Instant.ofEpochMilli(to))
    }

    @PluginMethod
    fun readExercise(call: PluginCall) {
        scope.launch {
            try {
                val c = HealthConnectClient.getOrCreate(context)
                val out = JSArray()
                var token: String? = null
                do {
                    val r = c.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range(call), pageSize = 200, pageToken = token))
                    for (e in r.records) {
                        val o = JSObject()
                        o.put("id", e.metadata.id)
                        o.put("start", e.startTime.toEpochMilli())
                        o.put("end", e.endTime.toEpochMilli())
                        o.put("type", ExerciseSessionRecord.EXERCISE_TYPE_INT_TO_STRING_MAP[e.exerciseType] ?: "other_workout")
                        o.put("title", e.title ?: "")
                        o.put("origin", e.metadata.dataOrigin.packageName)
                        out.put(o)
                    }
                    token = r.pageToken
                } while (!token.isNullOrEmpty())
                val ret = JSObject(); ret.put("sessions", out); call.resolve(ret)
            } catch (e: Throwable) {
                call.reject("readExercise failed: $e")
            }
        }
    }

    /** one total per calendar day (Health Connect de-duplicates overlapping sources) */
    @PluginMethod
    fun readSteps(call: PluginCall) {
        scope.launch {
            try {
                val c = HealthConnectClient.getOrCreate(context)
                val z = ZoneId.systemDefault()
                val from = Instant.ofEpochMilli(call.getLong("from") ?: (System.currentTimeMillis() - 3 * 86400000L))
                val to = Instant.ofEpochMilli(call.getLong("to") ?: System.currentTimeMillis())
                val res = c.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(LocalDateTime.ofInstant(from, z).toLocalDate().atStartOfDay(), LocalDateTime.ofInstant(to, z)),
                    timeRangeSlicer = Period.ofDays(1)))
                val out = JSArray()
                for (d in res) {
                    val o = JSObject()
                    o.put("date", d.startTime.toLocalDate().toString())
                    o.put("count", d.result[StepsRecord.COUNT_TOTAL] ?: 0L)
                    out.put(o)
                }
                val ret = JSObject(); ret.put("days", out); call.resolve(ret)
            } catch (e: Throwable) {
                call.reject("readSteps failed: $e")
            }
        }
    }

    @PluginMethod
    fun readSleep(call: PluginCall) {
        scope.launch {
            try {
                val c = HealthConnectClient.getOrCreate(context)
                val out = JSArray()
                val r = c.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range(call), pageSize = 100))
                for (s in r.records) {
                    val o = JSObject()
                    o.put("id", s.metadata.id)
                    o.put("start", s.startTime.toEpochMilli())
                    o.put("end", s.endTime.toEpochMilli())
                    o.put("origin", s.metadata.dataOrigin.packageName)
                    out.put(o)
                }
                val ret = JSObject(); ret.put("sessions", out); call.resolve(ret)
            } catch (e: Throwable) {
                call.reject("readSleep failed: $e")
            }
        }
    }

    @PluginMethod
    fun readWeight(call: PluginCall) {
        scope.launch {
            try {
                val c = HealthConnectClient.getOrCreate(context)
                val r = c.readRecords(ReadRecordsRequest(WeightRecord::class, range(call), ascendingOrder = false, pageSize = 1))
                val ret = JSObject()
                val w = r.records.firstOrNull()
                if (w != null) {
                    ret.put("kg", w.weight.inKilograms)
                    ret.put("time", w.time.toEpochMilli())
                    ret.put("origin", w.metadata.dataOrigin.packageName)
                }
                call.resolve(ret)
            } catch (e: Throwable) {
                call.reject("readWeight failed: $e")
            }
        }
    }
}
