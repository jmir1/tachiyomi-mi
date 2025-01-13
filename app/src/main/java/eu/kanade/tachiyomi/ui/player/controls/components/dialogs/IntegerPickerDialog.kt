package eu.kanade.tachiyomi.ui.player.controls.components.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.presentation.core.components.WheelTextPicker

@Composable
fun IntegerPickerDialog(
    defaultValue: Int,
    minValue: Int,
    maxValue: Int,
    step: Int,
    nameFormat: String,
    title: String,
    onChange: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var newValue = defaultValue
    val values = (minValue..maxValue step step).toList()
    val items = values.map { String.format(nameFormat, it) }.toImmutableList()

    PlayerDialog(
        title = title,
        modifier = Modifier.fillMaxWidth(fraction = 0.5f),
        onConfirmRequest = null,
        onDismissRequest = {
            onChange(newValue)
            onDismissRequest()
        },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            WheelTextPicker(
                modifier = Modifier.align(Alignment.Center),
                items = items,
                onSelectionChanged = { newValue = values[it] },
                startIndex = values.indexOfFirst { it == defaultValue }.coerceAtLeast(0),
            )
        }
    }
}
