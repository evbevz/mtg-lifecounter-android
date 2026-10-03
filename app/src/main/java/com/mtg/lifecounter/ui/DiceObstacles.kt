package com.mtg.lifecounter.ui

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.mtg.lifecounter.dice.DiceController
import com.mtg.lifecounter.dice.DiceMarble

/** Collision radius relative to the marble bounds, as in the iOS version. */
private const val MARBLE_HIT_SCALE = 0.45f

/** Marbles on screen that the dice bounces off, keyed by the composable that shows them. */
class DiceObstacles(private val dice: DiceController) {
    private val marbles = linkedMapOf<String, DiceMarble>()

    fun update(key: String, marble: DiceMarble) {
        if (marbles[key] == marble) return
        marbles[key] = marble
        dice.setMarbles(marbles.values.toList())
    }

    fun remove(key: String) {
        if (marbles.remove(key) != null) dice.setMarbles(marbles.values.toList())
    }
}

/** Registers this element as a round dice obstacle while it is in the composition. */
fun Modifier.diceObstacle(obstacles: DiceObstacles?, key: String): Modifier =
    if (obstacles == null) this else composed {
        DisposableEffect(obstacles, key) {
            onDispose { obstacles.remove(key) }
        }
        onGloballyPositioned { coordinates ->
            val position = coordinates.positionInRoot()
            val width = coordinates.size.width.toFloat()
            val height = coordinates.size.height.toFloat()
            obstacles.update(
                key,
                DiceMarble(position.x + width / 2f, position.y + height / 2f, width / 2f * MARBLE_HIT_SCALE)
            )
        }
    }
