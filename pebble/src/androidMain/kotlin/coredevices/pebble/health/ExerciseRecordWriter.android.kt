package coredevices.pebble.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.time.TimeRangeFilter
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.database.entity.OverlayDataEntity
import io.rebble.libpebblecommon.health.OverlayType
import java.time.Instant
import java.time.ZoneId

internal actual fun createExerciseRecordWriter(appContext: AppContext): ExerciseRecordWriter =
    HealthConnectExerciseRecordWriter(appContext.context.applicationContext)

private class HealthConnectExerciseRecordWriter(private val context: Context) : ExerciseRecordWriter {

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    override suspend fun write(activities: List<OverlayDataEntity>): Result<Unit> = runCatching {
        val version = System.currentTimeMillis()
        val zone = ZoneId.systemDefault().rules
        val records = activities.mapNotNull { activity ->
            if (activity.duration <= 0) return@mapNotNull null
            val (type, title) = when (OverlayType.fromValue(activity.type)) {
                OverlayType.Walk -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING to "Walk"
                OverlayType.Run -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING to "Run"
                OverlayType.OpenWorkout -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT to "Workout"
                else -> return@mapNotNull null
            }
            val start = Instant.ofEpochSecond(activity.startTime)
            val end = start.plusSeconds(activity.duration)
            ExerciseSessionRecord(
                startTime = start,
                startZoneOffset = zone.getOffset(start),
                endTime = end,
                endZoneOffset = zone.getOffset(end),
                exerciseType = type,
                title = title,
                metadata = Metadata.autoRecorded(
                    device = Device(type = Device.TYPE_WATCH),
                    clientRecordId = exerciseClientRecordId(activity),
                    clientRecordVersion = version,
                ),
            )
        }
        if (records.isNotEmpty()) client.insertRecords(records)
    }

    override suspend fun delete(activity: OverlayDataEntity): Result<Unit> = runCatching {
        client.deleteRecords(
            ExerciseSessionRecord::class,
            recordIdsList = emptyList(),
            clientRecordIdsList = listOf(exerciseClientRecordId(activity)),
        )
        // Records written before stable ids (via health-kmp) have no client id. Health
        // Connect only deletes the calling app's own records, so this can't touch workouts
        // recorded by other apps.
        val start = Instant.ofEpochSecond(activity.startTime)
        client.deleteRecords(
            ExerciseSessionRecord::class,
            TimeRangeFilter.between(start, start.plusSeconds(1)),
        )
    }
}
