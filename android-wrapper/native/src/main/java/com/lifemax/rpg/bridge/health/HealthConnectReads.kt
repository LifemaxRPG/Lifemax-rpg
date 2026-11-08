package com.lifemax.rpg.bridge.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.lifemax.rpg.core.adapters.DeviceClock
import com.lifemax.rpg.core.adapters.HealthReads
import com.lifemax.rpg.core.economy.HealthConnect
import java.time.Instant
import java.time.LocalDateTime
import java.time.Period
import kotlinx.coroutines.runBlocking

/**
 * [HealthReads] over the Health Connect client: the same reads, ranges and shapes as
 * `HealthSyncPlugin` (which the page keeps using), read-only. It never asks for permission and
 * never writes. Calls block: run them off the main thread, as a sync does.
 *
 * Steps are cut into calendar days in the [device] zone, the zone the economy's day keys use, where
 * the plugin cuts them in the system zone; they are the same zone unless the app overrides it.
 * Every failure is rethrown with the plugin's wording (`readSteps failed: ...`), which the sync
 * shows after "Sync failed: ".
 */
class HealthConnectReads(private val context: Context, private val device: DeviceClock) :
    HealthReads {
    private val permissions =
        linkedMapOf(
            "exercise" to HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            "steps" to HealthPermission.getReadPermission(StepsRecord::class),
            "sleep" to HealthPermission.getReadPermission(SleepSessionRecord::class),
            "weight" to HealthPermission.getReadPermission(WeightRecord::class),
        )

    private fun <T> read(name: String, block: suspend (HealthConnectClient) -> T): T =
        try {
            runBlocking { block(HealthConnectClient.getOrCreate(context)) }
        } catch (e: Throwable) {
            throw IllegalStateException("$name failed: $e", e)
        }

    override fun status(): HealthConnect.Status =
        try {
            val sdk = HealthConnectClient.getSdkStatus(context)
            val granted =
                if (sdk == HealthConnectClient.SDK_AVAILABLE)
                    runBlocking {
                        HealthConnectClient.getOrCreate(context)
                            .permissionController
                            .getGrantedPermissions()
                    }
                else emptySet()
            HealthConnect.Status(
                when (sdk) {
                    HealthConnectClient.SDK_AVAILABLE -> "available"
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "update"
                    else -> "unavailable"
                },
                permissions.filterValues { it in granted }.keys.toList(),
            )
        } catch (e: Throwable) {
            throw IllegalStateException("status failed: $e", e)
        }

    private fun range(from: Long, to: Long) =
        TimeRangeFilter.between(Instant.ofEpochMilli(from), Instant.ofEpochMilli(to))

    override fun exercise(from: Long, to: Long): List<HealthConnect.Workout> =
        read("readExercise") { client ->
            val out = mutableListOf<HealthConnect.Workout>()
            var token: String? = null
            do {
                val page =
                    client.readRecords(
                        ReadRecordsRequest(
                            ExerciseSessionRecord::class,
                            range(from, to),
                            pageSize = EXERCISE_PAGE,
                            pageToken = token,
                        )
                    )
                for (e in page.records) out +=
                    HealthConnect.Workout(
                        id = e.metadata.id,
                        type =
                            ExerciseSessionRecord.EXERCISE_TYPE_INT_TO_STRING_MAP[e.exerciseType]
                                ?: "other_workout",
                        start = e.startTime.toEpochMilli(),
                        end = e.endTime.toEpochMilli(),
                        title = e.title ?: "",
                        origin = e.metadata.dataOrigin.packageName,
                    )
                token = page.pageToken
            } while (!token.isNullOrEmpty())
            out
        }

    /** One total per calendar day (Health Connect de-duplicates overlapping sources). */
    override fun steps(from: Long, to: Long): List<HealthConnect.StepDay> =
        read("readSteps") { client ->
            val zone = device.zone()
            val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(from), zone)
            val end = LocalDateTime.ofInstant(Instant.ofEpochMilli(to), zone)
            client
                .aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter =
                            TimeRangeFilter.between(start.toLocalDate().atStartOfDay(), end),
                        timeRangeSlicer = Period.ofDays(1),
                    )
                )
                .map {
                    HealthConnect.StepDay(
                        it.startTime.toLocalDate().toString(),
                        (it.result[StepsRecord.COUNT_TOTAL] ?: 0L).toDouble(),
                    )
                }
        }

    override fun sleep(from: Long, to: Long): List<HealthConnect.SleepSession> =
        read("readSleep") { client ->
            client
                .readRecords(
                    ReadRecordsRequest(
                        SleepSessionRecord::class,
                        range(from, to),
                        pageSize = SLEEP_PAGE,
                    )
                )
                .records
                .map {
                    HealthConnect.SleepSession(
                        it.metadata.id,
                        it.startTime.toEpochMilli(),
                        it.endTime.toEpochMilli(),
                    )
                }
        }

    override fun weight(from: Long, to: Long): HealthConnect.Weight? =
        read("readWeight") { client ->
            client
                .readRecords(
                    ReadRecordsRequest(
                        WeightRecord::class,
                        range(from, to),
                        ascendingOrder = false,
                        pageSize = 1,
                    )
                )
                .records
                .firstOrNull()
                ?.let {
                    HealthConnect.Weight(it.weight.inKilograms, it.time.toEpochMilli().toDouble())
                }
        }

    private companion object {
        const val EXERCISE_PAGE = 200
        const val SLEEP_PAGE = 100
    }
}
