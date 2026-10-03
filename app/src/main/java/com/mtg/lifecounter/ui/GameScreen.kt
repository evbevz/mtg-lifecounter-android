package com.mtg.lifecounter.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.mtg.lifecounter.GameViewModel
import com.mtg.lifecounter.R
import com.mtg.lifecounter.ui.theme.CardNumbersBorderColor
import com.mtg.lifecounter.ui.theme.GaramondFamily
import kotlinx.coroutines.delay

// Layout proportions from iOS reference (768×1024)
private const val GAME_ASPECT = 3f / 4f

private const val CARD_X = 144f / 768f
private const val CARD_Y = 108f / 1024f
private const val CARD_W = 560f / 768f
private const val CARD_H = 721f / 1024f

private const val POISON_X = 25f / 768f

private const val BOTTOM_BASELINE = 0.892f

private const val MARBLE_AREA_LEFT = 10f / 768f
private const val MARBLE_AREA_WIDTH_RATIO = 0.5f
private const val MARBLE_DISPLAY_SCALE = 0.16f

private const val DICE_SIZE_RATIO = 80f / 768f
private const val DICE_RIGHT_OFFSET = 60f / 768f

private const val IDLE_TIMEOUT_MS = 60_000L

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val activePlayer = viewModel.activePlayer
    val activeIndex = viewModel.activePlayerIndex

    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showIdleScreen by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.players, viewModel.activePlayerIndex, viewModel.diceValue) {
        lastInteraction = System.currentTimeMillis()
        showIdleScreen = false
    }

    LaunchedEffect(lastInteraction) {
        delay(IDLE_TIMEOUT_MS)
        showIdleScreen = true
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

            Box(modifier = Modifier.size(gameW, gameH)) {
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

                val marbleRes = if (activeIndex == 0) R.drawable.marble_blue else R.drawable.marble_red

                LifeGrid(
                    lifeBase = activePlayer.lifeBase,
                    currentLife = activePlayer.life,
                    marbleRes = marbleRes,
                    onLifeSelected = { viewModel.setLife(it) },
                    onScrollPage = { viewModel.scrollLifeBase(it) },
                    modifier = Modifier
                        .offset(x = cardX, y = cardY)
                        .size(width = cardW, height = cardH)
                )

                // --- Poison thermometer (left side) ---
                val poisonX = gameW * POISON_X
                val poisonH = gameH * 0.52f
                val poisonW = poisonH * 0.065f
                val poisonY = cardY + cardH - poisonH + gameH * 0.01f

                PoisonView(
                    value = activePlayer.poison,
                    onValueChange = { viewModel.adjustPoison(it) },
                    modifier = Modifier
                        .offset(x = poisonX, y = poisonY)
                        .size(width = poisonW, height = poisonH)
                )

                // --- Bottom controls ---
                val bottomY = gameH * BOTTOM_BASELINE
                val marbleSize = gameW * MARBLE_DISPLAY_SCALE
                val buttonsAreaLeft = gameW * MARBLE_AREA_LEFT
                val buttonsAreaW = gameW * MARBLE_AREA_WIDTH_RATIO - buttonsAreaLeft

                // Player marble buttons
                for (i in 0 until 2) {
                    val centerX = buttonsAreaLeft + buttonsAreaW / 2f * (2f * i + 1f)
                    val btnX = centerX - marbleSize / 2
                    val btnY = bottomY - marbleSize / 2

                    if (i == activeIndex) {
                        // Active player: show bubble (marble is on the grid)
                        Image(
                            painter = painterResource(R.drawable.bubble),
                            contentDescription = "Player ${i + 1} slot",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .offset(x = btnX, y = btnY)
                                .size(marbleSize)
                        )
                    } else {
                        // Inactive player: show marble button with life total
                        Box(
                            modifier = Modifier
                                .offset(x = btnX, y = btnY)
                                .size(marbleSize)
                                .clickable { viewModel.switchPlayer(i) },
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
                    diceValue = viewModel.diceValue,
                    isRolling = viewModel.isDiceRolling,
                    onRoll = { viewModel.rollDice() },
                    modifier = Modifier
                        .offset(x = diceX, y = diceY)
                        .size(diceSize)
                )
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
