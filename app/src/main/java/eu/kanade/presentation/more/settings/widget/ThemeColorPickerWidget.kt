package eu.kanade.presentation.more.settings.widget

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.ColorEnvelope
import com.github.skydoves.colorpicker.compose.ColorPickerController
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import eu.kanade.domain.ui.model.AppTheme
import kotlin.math.roundToInt

@Composable
internal fun ThemeColorPickerWidget(
    initialColor: Color,
    controller: ColorPickerController,
    onItemClick: (Color, AppTheme) -> Unit,
) {
    var selectedColor by remember { mutableStateOf(initialColor) }
    var showConfirmButton by remember { mutableStateOf(false) }

    val wheelSize = with(LocalDensity.current) { 20.dp.toPx().roundToInt() }
    val wheelStrokeWidth = with(LocalDensity.current) { 2.dp.toPx() }

    // Remember a wheel bitmap
    val wheelBitmap = remember(wheelSize, wheelStrokeWidth) {
        val bitmap = Bitmap.createBitmap(wheelSize, wheelSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = wheelStrokeWidth
            isAntiAlias = true
        }

        // Draw the circle for wheel indicator
        canvas.drawCircle(
            wheelSize / 2f,
            wheelSize / 2f,
            wheelSize / 2f - wheelStrokeWidth,
            paint,
        )
        bitmap.asImageBitmap()
    }

    BasePreferenceWidget(
        subcomponent = {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(modifier = Modifier) {
                    HsvColorPicker(
                        modifier = Modifier
                            .size(with(LocalDensity.current) { 300.dp }),
                        controller = controller,
                        wheelImageBitmap = wheelBitmap,
                        initialColor = initialColor,
                        onColorChanged = { colorEnvelope: ColorEnvelope ->
                            selectedColor = colorEnvelope.color
                            showConfirmButton = true
                        },
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                CustomBrightnessSlider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    controller = controller,
                    initialColor = initialColor,
                )
                AnimatedVisibility(
                    visible = showConfirmButton,
                    enter = fadeIn() + expandVertically(),
                ) {
                    Button(
                        onClick = {
                            onItemClick(selectedColor, AppTheme.CUSTOM)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .height(48.dp),
                        content = {
                            Text("Confirm Color")
                        },
                    )
                }
            }
        },
    )
}
