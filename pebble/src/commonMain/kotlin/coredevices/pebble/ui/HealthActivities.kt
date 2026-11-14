package coredevices.pebble.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.rebble.libpebblecommon.database.entity.OverlayDataEntity
import io.rebble.libpebblecommon.health.OverlayType
import kotlinx.datetime.TimeZone

private val EditableTypes = listOf(OverlayType.Walk, OverlayType.Run, OverlayType.OpenWorkout)

private fun OverlayType.displayName(): String = when (this) {
    OverlayType.Walk -> "Walk"
    OverlayType.Run -> "Run"
    OverlayType.OpenWorkout -> "Workout"
    else -> name
}

/** Lists the day's watch-detected activities; tapping one opens [EditActivityDialog]. */
@Composable
internal fun ActivitiesCard(
    activities: List<OverlayDataEntity>,
    onSave: (original: OverlayDataEntity, type: OverlayType, startEpochSec: Long, endEpochSec: Long) -> Unit,
    onDelete: (OverlayDataEntity) -> Unit,
) {
    val tz = remember { TimeZone.currentSystemDefault() }
    var editing by remember { mutableStateOf<OverlayDataEntity?>(null) }

    Surface(shape = CardShape, color = ActivityBgColor, shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                "Activities",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (activities.isEmpty()) {
                Text(
                    "No activities detected by the watch on this day",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            activities.forEachIndexed { i, activity ->
                if (i > 0) HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                val type = OverlayType.fromValue(activity.type)
                Row(
                    Modifier.fillMaxWidth().clickable { editing = activity }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            type?.displayName() ?: "Activity",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${formatTimeOfDay(activity.startTime, tz)} – " +
                                "${formatTimeOfDay(activity.startTime + activity.duration, tz)} · " +
                                "${activity.duration / 60} min · ${activity.steps} steps",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("Edit", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    editing?.let { activity ->
        EditActivityDialog(
            activity = activity,
            tz = tz,
            onDismiss = { editing = null },
            onSave = { type, start, end ->
                onSave(activity, type, start, end)
                editing = null
            },
            onDelete = {
                onDelete(activity)
                editing = null
            },
        )
    }
}

/**
 * Edits an activity's type and trims its start/end in one-minute steps. Steps, distance and
 * calories stay as the watch recorded them. Changes also apply to the health platform record.
 */
@Composable
private fun EditActivityDialog(
    activity: OverlayDataEntity,
    tz: TimeZone,
    onDismiss: () -> Unit,
    onSave: (type: OverlayType, startEpochSec: Long, endEpochSec: Long) -> Unit,
    onDelete: () -> Unit,
) {
    val originalEnd = activity.startTime + activity.duration
    var type by remember { mutableStateOf(OverlayType.fromValue(activity.type) ?: OverlayType.Walk) }
    var start by remember { mutableLongStateOf(activity.startTime) }
    var end by remember { mutableLongStateOf(originalEnd) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete activity?") },
            text = {
                Text(
                    "This removes it from Gravel and from Health Connect. " +
                        "The watch keeps its own history."
                )
            },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit activity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    EditableTypes.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = type == t,
                            onClick = { type = t },
                            shape = SegmentedButtonDefaults.itemShape(i, EditableTypes.size),
                        ) { Text(t.displayName()) }
                    }
                }
                // Trimming only: start can move later, end earlier, keeping at least a minute.
                TimeStepper(
                    label = "Start",
                    value = formatTimeOfDay(start, tz),
                    canDecrease = start - 60 >= activity.startTime,
                    canIncrease = start + 60 <= end - 60,
                    onDecrease = { start -= 60 },
                    onIncrease = { start += 60 },
                )
                TimeStepper(
                    label = "End",
                    value = formatTimeOfDay(end, tz),
                    canDecrease = end - 60 >= start + 60,
                    canIncrease = end + 60 <= originalEnd,
                    onDecrease = { end -= 60 },
                    onIncrease = { end += 60 },
                )
                Text(
                    "${(end - start) / 60} min. Steps and distance stay as the watch recorded them.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Delete activity", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(type, start, end) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TimeStepper(
    label: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(56.dp))
        IconButton(onClick = onDecrease, enabled = canDecrease) {
            Icon(Icons.Default.Remove, contentDescription = "$label one minute earlier")
        }
        Text(value, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onIncrease, enabled = canIncrease) {
            Icon(Icons.Default.Add, contentDescription = "$label one minute later")
        }
        Spacer(Modifier.weight(1f))
    }
}
