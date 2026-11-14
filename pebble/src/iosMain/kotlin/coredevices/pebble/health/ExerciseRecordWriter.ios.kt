package coredevices.pebble.health

import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.database.entity.OverlayDataEntity

// Mechanical actual so the iOS target keeps compiling; iOS is unmaintained in this fork.
internal actual fun createExerciseRecordWriter(appContext: AppContext): ExerciseRecordWriter =
    object : ExerciseRecordWriter {
        override suspend fun write(activities: List<OverlayDataEntity>) = Result.success(Unit)
        override suspend fun delete(activity: OverlayDataEntity) = Result.success(Unit)
    }
