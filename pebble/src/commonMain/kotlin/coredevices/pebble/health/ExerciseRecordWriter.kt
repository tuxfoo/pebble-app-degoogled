package coredevices.pebble.health

import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.database.entity.OverlayDataEntity

/**
 * Fork seam. Writes watch-detected workouts to the platform with a stable per-activity id, so
 * an activity edited or deleted in the app can update or remove exactly its own record.
 * health-kmp can't do either: it drops the client record id from metadata and has no delete.
 */
interface ExerciseRecordWriter {
    suspend fun write(activities: List<OverlayDataEntity>): Result<Unit>

    /**
     * Removes this activity's record. Also covers records written before stable ids existed,
     * by deleting this app's own exercise record that starts at the same second.
     */
    suspend fun delete(activity: OverlayDataEntity): Result<Unit>
}

internal expect fun createExerciseRecordWriter(appContext: AppContext): ExerciseRecordWriter

/** Stable id for an activity: its key in overlay_data (type + start time). */
internal fun exerciseClientRecordId(activity: OverlayDataEntity): String =
    "pebble-exercise-${activity.type}-${activity.startTime}"
