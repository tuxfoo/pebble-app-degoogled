package io.rebble.libpebblecommon.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_data")
data class HealthDataEntity(
    @PrimaryKey
    val timestamp: Long,
    val steps: Int,
    val orientation: Int,
    val intensity: Int,
    val lightIntensity: Int,
    val activeMinutes: Int,
    val restingGramCalories: Int,
    val activeGramCalories: Int,
    val distanceCm: Int,
    val heartRate: Int = 0,
    val heartRateZone: Int = 0,
    val heartRateWeight: Int = 0
)

@Entity(
    tableName = "overlay_data",
    primaryKeys = ["startTime", "type"]
)
data class OverlayDataEntity(
    val startTime: Long,
    val duration: Long,
    val type: Int,
    val steps: Int,
    val restingKiloCalories: Int,
    val activeKiloCalories: Int,
    val distanceCm: Int,
    val offsetUTC: Int
)

/**
 * Original key (startTime + type) of an overlay the user edited or deleted on the phone. The
 * watch can re-send overlays (see insertOverlayDataWithDeduplication); a re-sent copy of a
 * suppressed key is ignored so the edit or deletion sticks. An edit is stored as a new
 * [OverlayDataEntity] row under its new key.
 */
@Entity(
    tableName = "suppressed_overlay",
    primaryKeys = ["startTime", "type"]
)
data class SuppressedOverlayEntity(
    val startTime: Long,
    val type: Int,
)
