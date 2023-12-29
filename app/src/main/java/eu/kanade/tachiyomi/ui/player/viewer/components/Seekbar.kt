package eu.kanade.tachiyomi.ui.player.viewer.components

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import dev.vivvvek.seeker.Seeker
import dev.vivvvek.seeker.SeekerDefaults
import dev.vivvvek.seeker.Segment
import `is`.xyz.mpv.MPVView.Chapter

@Composable
fun Seekbar(
    position: Float,
    duration: Float,
    readAheadValue: Float,
    chapters: List<Chapter>,
    onValueChange: (Float, Boolean) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val range = 0F..duration
    val validSegments = chapters.toSegments().filter { it.start in range }
    var mutableValue by remember { mutableFloatStateOf(position) }
    val interactionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()
    val gap by animateDpAsState(if (isDragging) 5.dp else 2.dp, label = "gap")
    val thumbRadius by animateDpAsState(if (isDragging) 10.dp else 8.dp, label = "thumbRadius")
    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 4.dp,
        label = "trackHeight",
    )
    var isSeeked by remember { mutableStateOf(false) }

    return Seeker(
        value = position,
        readAheadValue = readAheadValue,
        range = range,
        onValueChangeFinished = {
            if (isSeeked) {
                onValueChangeFinished(mutableValue)
                isSeeked = false
            }
        },
        onValueChange = {
            mutableValue = it
            if (isDragging) {
                val wasDragging = isSeeked
                isSeeked = true
                onValueChange(mutableValue, wasDragging)
            } else {
                onValueChangeFinished(mutableValue)
            }
        },
        segments = validSegments,
        colors = SeekerDefaults.seekerColors(
            progressColor = MaterialTheme.colorScheme.primary,
            readAheadColor = MaterialTheme.colorScheme.onSurface,
            trackColor = MaterialTheme.colorScheme.surface,
            thumbColor = MaterialTheme.colorScheme.primary,
        ),
        dimensions = SeekerDefaults.seekerDimensions(
            trackHeight = trackHeight,
            gap = gap,
            thumbRadius = thumbRadius,
        ),
        interactionSource = interactionSource,
        modifier = modifier,
    )
}

@Composable
private fun List<Chapter>.toSegments(): List<Segment> {
    return this.sortedBy { it.time }.map {
        // Color for AniSkip chapters
        val color = if (it.index == -2) {
            MaterialTheme.colorScheme.tertiary
        } else {
            Color.Unspecified
        }
        Segment(
            it.title ?: "",
            it.time.toFloat(),
            color,
        )
    }
}
