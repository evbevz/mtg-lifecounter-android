package com.mtg.lifecounter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.mtg.lifecounter.R
import kotlin.math.abs

/** A vertical slide of 1/25 of the counter height changes poison by one, as in the iOS version. */
private const val SLIDE_STEPS_PER_HEIGHT = 25f

private val poisonResIds = intArrayOf(
    R.drawable.poison_0, R.drawable.poison_1, R.drawable.poison_2,
    R.drawable.poison_3, R.drawable.poison_4, R.drawable.poison_5,
    R.drawable.poison_6, R.drawable.poison_7, R.drawable.poison_8,
    R.drawable.poison_9, R.drawable.poison_10, R.drawable.poison_11,
    R.drawable.poison_12, R.drawable.poison_13, R.drawable.poison_14,
    R.drawable.poison_15, R.drawable.poison_16, R.drawable.poison_17,
    R.drawable.poison_18, R.drawable.poison_19, R.drawable.poison_20
)

/**
 * Poison thermometer view that displays the corresponding Poison_N.png image.
 * Touch handling matches the original iOS PoisonView:
 * - Tap upper half → +1 poison
 * - Tap lower half → −1 poison
 * - Vertical swipe adjusts in steps (up = +1, down = −1)
 * - Horizontal movement beyond threshold cancels tap
 */
@Composable
fun PoisonView(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val resId = poisonResIds[value.coerceIn(0, 20)]
    // The gesture outlives value changes, so it must not restart mid-slide
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val stepThreshold = size.height / SLIDE_STEPS_PER_HEIGHT
                    val startX = down.position.x
                    var lastStepY = down.position.y
                    var hasMoved = false

                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        // Each step of the slide changes the value by one: up adds, down removes
                        var dy = change.position.y - lastStepY
                        while (abs(dy) > stepThreshold) {
                            hasMoved = true
                            currentOnValueChange(if (dy > 0) -1 else 1)
                            lastStepY += if (dy > 0) stepThreshold else -stepThreshold
                            dy = change.position.y - lastStepY
                        }
                        if (!hasMoved && abs(change.position.x - startX) > stepThreshold) {
                            hasMoved = true
                        }
                        change.consume()
                    }

                    if (!hasMoved) {
                        currentOnValueChange(if (down.position.y < size.height / 2f) 1 else -1)
                    }
                }
            }
    ) {
        Image(
            painter = painterResource(resId),
            contentDescription = "Poison: $value",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Dice home area using the dice-place.png icon.
 * The 3D dice rests here; tapping the area brings the dice back.
 */
@Composable
fun DiceButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        ) { onClick() }
    ) {
        Image(
            painter = painterResource(R.drawable.dice_place),
            contentDescription = "Return dice",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}
