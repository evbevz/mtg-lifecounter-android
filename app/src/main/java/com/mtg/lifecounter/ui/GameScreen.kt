package com.mtg.lifecounter.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.mtg.lifecounter.GameViewModel
import com.mtg.lifecounter.R
import com.mtg.lifecounter.dice.DiceController
import com.mtg.lifecounter.ui.theme.CardNumbersBorderColor
import com.mtg.lifecounter.ui.theme.GaramondFamily
import kotlinx.coroutines.delay

// Layout proportions from iOS reference (768×1024)
private const val GAME_ASPECT = 3f / 4f

private const val CARD_X = 144f / 768f
private const val CARD_Y = 108f / 1024f
private const val CARD_W = 560f / 768f
private const val CARD_H = 721f / 1024f

private const val BOTTOM_BASELINE = 0.892f

private const val GRID_COLUMNS = 4
private const val MARBLE_DISPLAY_SCALE = 0.4f
private const val BUBBLE_DISPLAY_SCALE = 0.16f

private const val DICE_SIZE_RATIO = 80f / 768f
private const val DICE_RIGHT_OFFSET = 60f / 768f

private const val IDLE_TIMEOUT_MS = 60_000L

/** Width / height of Poison_N.png (93 × 670). */
private const val POISON_ASPECT = 93f / 670f

/** Player switch timings; the flip matches CARD_ROTATE_DURATION of the iOS version. */
private const val MARBLE_FADE_MS = 200
private const val CARD_FLIP_MS = 400

/** Marbles fade and shrink together: 0 is hidden at zero size, 1 is fully visible at original size. */
private fun GraphicsLayerScope.marbleAppearance(progress: Float) {
    alpha = progress
    scaleX = progress
    scaleY = progress
}

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
    dice: DiceController? = null
) {
    val activePlayer = viewModel.activePlayer
    val activeIndex = viewModel.activePlayerIndex
    val obstacles = remember(dice) { dice?.let(::DiceObstacles) }

    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showIdleScreen by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.players, viewModel.activePlayerIndex) {
        lastInteraction = System.currentTimeMillis()
        showIdleScreen = false
    }

    LaunchedEffect(lastInteraction) {
        delay(IDLE_TIMEOUT_MS)
        showIdleScreen = true
    }

    DisposableEffect(dice) {
        dice?.onUserInteraction = {
            lastInteraction = System.currentTimeMillis()
            showIdleScreen = false
        }
        onDispose { dice?.onUserInteraction = null }
    }

    LaunchedEffect(dice, showIdleScreen) {
        dice?.setDiceVisible(!showIdleScreen)
    }

    // Player whose card is on screen; lags behind activeIndex while the switch animation runs
    var shownIndex by remember { mutableIntStateOf(activeIndex) }
    // Poison counter switches only after the whole switch animation has finished
    var poisonIndex by remember { mutableIntStateOf(activeIndex) }
    val shownPlayer = viewModel.players[shownIndex]
    val marblesAlpha = remember { Animatable(1f) }
    val cardRotation = remember { Animatable(0f) }
    var isSwitching by remember { mutableStateOf(false) }

    LaunchedEffect(activeIndex) {
        if (activeIndex == shownIndex) return@LaunchedEffect
        isSwitching = true
        marblesAlpha.animateTo(0f, tween(MARBLE_FADE_MS))
        cardRotation.animateTo(90f, tween(CARD_FLIP_MS / 2, easing = LinearEasing))
        shownIndex = activeIndex
        cardRotation.snapTo(-90f)
        // Let the new player's card compose before timing the second half, so it isn't skipped
        withFrameNanos { }
        cardRotation.animateTo(0f, tween(CARD_FLIP_MS / 2, easing = LinearEasing))
        marblesAlpha.animateTo(1f, tween(MARBLE_FADE_MS))
        poisonIndex = activeIndex
        isSwitching = false
    }

    Box(modifier = modifier.background(Color.Black)) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val screenW = maxWidth
            val screenH = maxHeight
            val screenAspect = screenW / screenH

            val gameW: Dp
            val gameH: Dp
            if (screenAspect > GAME_ASPECT) {
                gameH = screenH
                gameW = screenH * GAME_ASPECT
            } else {
                gameW = screenW
                gameH = screenW / GAME_ASPECT
            }

            Box(
                modifier = Modifier
                    .size(gameW, gameH)
                    .onGloballyPositioned { coordinates ->
                        val position = coordinates.positionInRoot()
                        dice?.setGameArea(
                            position.x,
                            position.y,
                            coordinates.size.width.toFloat(),
                            coordinates.size.height.toFloat()
                        )
                    }
            ) {
                // --- Background frame (BackGrHole.png) ---
                Image(
                    painter = painterResource(R.drawable.backgrhole),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )

                // --- Card grid (Field.png + numbers + marble) ---
                val cardX = gameW * CARD_X
                val cardY = gameH * CARD_Y
                val cardW = gameW * CARD_W
                val cardH = gameH * CARD_H

                val marbleRes = if (shownIndex == 0) R.drawable.marble_blue else R.drawable.marble_red
                val density = LocalDensity.current.density
                val marbleSize = gameW * MARBLE_DISPLAY_SCALE

                LifeGrid(
                    lifeBase = shownPlayer.lifeBase,
                    currentLife = shownPlayer.life,
                    marbleRes = marbleRes,
                    marbleSize = marbleSize,
                    onLifeSelected = { viewModel.setLife(it) },
                    onLifeBaseChange = { viewModel.setLifeBase(it) },
                    modifier = Modifier
                        .offset(x = cardX, y = cardY)
                        .size(width = cardW, height = cardH)
                        .graphicsLayer {
                            rotationY = cardRotation.value
                            cameraDistance = 12f * density
                        },
                    marbleKey = shownIndex,
                    marbleModifier = Modifier
                        .diceObstacle(obstacles, "active")
                        .graphicsLayer { marbleAppearance(marblesAlpha.value) }
                )

                // --- Poison thermometer (left side) ---
                val poisonH = gameH * 0.52f * 1.45f
                val poisonW = poisonH * POISON_ASPECT
                // Centered between the left screen edge and the left edge of the grid
                val poisonX = cardX / 2 - poisonW / 2
                val poisonY = cardY + cardH - poisonH + gameH * 0.01f

                PoisonView(
                    value = viewModel.players[poisonIndex].poison,
                    onValueChange = { viewModel.adjustPoison(it) },
                    modifier = Modifier
                        .offset(x = poisonX, y = poisonY)
                        .size(width = poisonW, height = poisonH)
                )

                // --- Bottom controls ---
                val bottomY = gameH * BOTTOM_BASELINE
                val bubbleSize = gameW * BUBBLE_DISPLAY_SCALE
                // Player marble buttons
                for (i in 0 until 2) {
                    // Slots sit under grid columns 1 and 2
                    val centerX = cardX + cardW / GRID_COLUMNS * (i + 0.5f)
                    val btnX = centerX - marbleSize / 2
                    val btnY = bottomY - marbleSize / 2

                    // Marble place bubble stays visible under the slot
                    Image(
                        painter = painterResource(R.drawable.bubble),
                        contentDescription = "Player ${i + 1} slot",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .offset(x = centerX - bubbleSize / 2, y = bottomY - bubbleSize / 2)
                            .size(bubbleSize)
                    )

                    if (i != shownIndex) {
                        // Inactive player: show marble button with life total
                        Box(
                            modifier = Modifier
                                .offset(x = btnX, y = btnY)
                                .size(marbleSize)
                                .diceObstacle(obstacles, "player$i")
                                .graphicsLayer { marbleAppearance(marblesAlpha.value) }
                                .clickable {
                                    dice?.returnToDefaultPlace()
                                    viewModel.switchPlayer(i)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(
                                    if (i == 0) R.drawable.marble_blue else R.drawable.marble_red
                                ),
                                contentDescription = "Player ${i + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            Text(
                                text = "${viewModel.players[i].life}",
                                fontFamily = GaramondFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = (marbleSize.value * 0.35f).sp,
                                color = CardNumbersBorderColor.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // --- Dice ---
                val diceSize = gameW * DICE_SIZE_RATIO
                val diceX = gameW - gameW * DICE_RIGHT_OFFSET - diceSize
                val diceY = bottomY - diceSize / 2

                DiceButton(
                    onClick = { dice?.returnToDefaultPlace() },
                    modifier = Modifier
                        .offset(x = diceX, y = diceY)
                        .size(diceSize)
                        .onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInRoot()
                            dice?.setDefaultPlace(
                                position.x + coordinates.size.width / 2f,
                                position.y + coordinates.size.height / 2f
                            )
                        }
                )

                // Touches are ignored until the player switch animation finishes
                if (isSwitching) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent().changes.forEach { it.consume() }
                                    }
                                }
                            }
                    )
                }
            }
        }

        // --- Idle lock screen ---
        AnimatedVisibility(
            visible = showIdleScreen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        showIdleScreen = false
                        lastInteraction = System.currentTimeMillis()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${activePlayer.life}",
                    fontSize = 120.sp,
                    fontFamily = GaramondFamily,
                    color = Color.Gray
                )
            }
        }
    }
}
