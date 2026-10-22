package app.saathi.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.saathi.ui.theme.reduceMotion

/**
 * Saathi's motion, in one place. Arrivals decelerate on the same curve the ajrakh star stamps with;
 * exits are quicker than entrances. With the system's Remove animations on, durations drop to zero:
 * colour and text still change, nothing travels.
 */
internal val Arrive = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** Routine state change. */
internal const val MOVE_MS = 260
/** Leaving is quicker than arriving. */
internal const val LEAVE_MS = 160
/** A layout change the eye should follow, such as the alert unfolding. */
internal const val UNFOLD_MS = 320

/** [ms], or zero when the system asks for no animations. */
@Composable
internal fun motionMs(ms: Int): Int = if (reduceMotion()) 0 else ms

/**
 * A check that draws itself, start to finish, the moment [done] turns true while it is on screen.
 * Shown already drawn when it first appears done, so opening the app later doesn't replay it.
 */
@Composable
internal fun DrawnCheck(done: Boolean, color: Color, modifier: Modifier = Modifier) {
    val still = reduceMotion()
    val progress = remember { Animatable(if (done) 1f else 0f) }
    LaunchedEffect(done) {
        when {
            !done -> progress.snapTo(0f)
            still -> progress.snapTo(1f)
            progress.value < 1f -> progress.animateTo(1f, tween(360, easing = Arrive))
        }
    }
    Canvas(modifier) {
        if (progress.value <= 0f) return@Canvas
        val w = size.width; val h = size.height
        val full = Path().apply {
            moveTo(w * 0.2f, h * 0.53f); lineTo(w * 0.42f, h * 0.74f); lineTo(w * 0.8f, h * 0.3f)
        }
        val measure = PathMeasure().apply { setPath(full, false) }
        val part = Path()
        measure.getSegment(0f, measure.length * progress.value, part, true)
        drawPath(part, color, style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
