package eu.kanade.presentation.more.settings.widget

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.ColorPickerController

@Composable
fun CustomBrightnessSlider(
    initialColor: Color,
    controller: ColorPickerController,
    modifier: Modifier = Modifier,
) {
    // Define your colors and sizes directly
    val borderColor = Color.LightGray // Color for the slider border
    val thumbRadius = 12.dp // Example thumb radius
    val trackHeight = 4.dp // Example track height
    val borderSize = 1.dp // Example border size for the slider

    // Set up the paint for the thumb (wheel)
    val wheelPaint = Paint().apply {
        color = Color.White
        alpha = 1.0f
    }

    // This function creates the ImageBitmap for the gradient background of the slider
    @Composable
    fun rememberSliderGradientBitmap(
        width: Dp,
        height: Dp,
        startColor: Color,
        endColor: Color,
    ): ImageBitmap {
        val sizePx = with(LocalDensity.current) { IntSize(width.roundToPx(), height.roundToPx()) }
        return remember(sizePx, startColor, endColor) {
            ImageBitmap(sizePx.width, sizePx.height, ImageBitmapConfig.Argb8888).apply {
                val canvas = Canvas(this)
                val shader = LinearGradientShader(
                    colors = listOf(startColor, endColor),
                    from = Offset(0f, 0f),
                    to = Offset(sizePx.width.toFloat(), 0f),
                    tileMode = TileMode.Clamp,
                )
                val paint = Paint().apply {
                    this.shader = shader
                }
                canvas.drawRect(
                    0f,
                    0f,
                    sizePx.width.toFloat(),
                    sizePx.height.toFloat(),
                    paint,
                )
            }
        }
    }

    // Obtain the Composable's size for the gradient background
    val sliderWidth = 20.dp // Example width, adjust to your needs
    val sliderHeight = thumbRadius * 2 // The height is double the thumb radius
    val gradientBitmap = rememberSliderGradientBitmap(
        width = sliderWidth, // Subtract the thumb radii from the total width
        height = trackHeight,
        startColor = Color.White,
        endColor = Color.White,
    )

    BrightnessSlider(
        modifier = modifier
            .height(sliderHeight)
            .fillMaxWidth()
            .padding(horizontal = thumbRadius), // Padding equals thumb radius
        controller = controller,
        initialColor = initialColor,
        borderRadius = thumbRadius, // Use thumbRadius for the rounded corners
        borderSize = borderSize,
        borderColor = borderColor, // Use borderColor for the slider border
        wheelRadius = thumbRadius,
        wheelColor = Color.White, // Thumb (wheel) color
        wheelImageBitmap = gradientBitmap, // Use the generated gradient bitmap as the background
        wheelAlpha = 1.0f, // Full opacity for the thumb
        wheelPaint = wheelPaint, // Use the defined wheel paint
    )
}
