package com.mtg.lifecounter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import com.mtg.lifecounter.R
import kotlin.math.abs

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
    var viewHeight by remember { mutableFloatStateOf(1f) }

    Box(
        modifier = modifier
            .onSizeChanged { viewHeight = it.height.toFloat() }
            .pointerInput(value) {
                var startX = 0f
                var startY = 0f
                var lastStepY = 0f
                var hasMoved = false
                val stepThreshold = viewHeight / 25f

                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        startX = down.position.x
                        startY = down.position.y
                        lastStepY = startY
                        hasMoved = false

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                val dy = change.position.y - lastStepY
                                if (abs(dy) > stepThreshold) {
                                    hasMoved = true
                                    if (dy > 0) onValueChange(-1)
                                    else onValueChange(1)
                                    lastStepY = change.position.y
                                }
                                if (!hasMoved && abs(change.position.x - startX) > stepThreshold) {
                                    hasMoved = true
                                }
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })

                        if (!hasMoved) {
                            if (startY < viewHeight / 2f) onValueChange(1)
                            else onValueChange(-1)
                        }
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
