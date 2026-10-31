package dev.brahmkshatriya.echo.app.ui.player.song

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp

enum class HeroDragDirection(val sign: Float) {
    Down(1f),
    Up(-1f),
}

private val HeroModeDragFlingVelocity = 800.dp

/**
 * Claims vertical drags in [direction] only. Drags the other way are left unconsumed so the player
 * list and sheet keep handling them, and horizontal drags stay with the pager.
 */
@Composable
fun Modifier.heroModeDrag(
    enabled: Boolean,
    direction: HeroDragDirection,
    distancePx: () -> Float,
    onStart: () -> Unit,
    onProgress: (Float) -> Unit,
    onRelease: (commit: Boolean) -> Unit,
): Modifier {
    if (!enabled) return this
    val currentDistancePx by rememberUpdatedState(distancePx)
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnProgress by rememberUpdatedState(onProgress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    return pointerInput(direction) {
        val sign = direction.sign
        val flingVelocity = HeroModeDragFlingVelocity.toPx()
        fun fraction(travel: Float) =
            (travel / currentDistancePx().coerceAtLeast(1f)).coerceIn(0f, 1f)

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var travel = 0f
            val slopChange =
                awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
                    if (overSlop * sign > 0f) {
                        change.consume()
                        travel = overSlop * sign
                    }
                } ?: return@awaitEachGesture

            var released = false
            currentOnStart()
            try {
                currentOnProgress(fraction(travel))
                val completed =
                    verticalDrag(slopChange.id) { change ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        travel += change.positionChange().y * sign
                        change.consume()
                        currentOnProgress(fraction(travel))
                    }
                val velocity = tracker.calculateVelocity().y * sign
                val commit =
                    completed &&
                        velocity > -flingVelocity &&
                        (fraction(travel) > 0.5f || velocity > flingVelocity)
                released = true
                currentOnRelease(commit)
            } finally {
                if (!released) currentOnRelease(false)
            }
        }
    }
}
