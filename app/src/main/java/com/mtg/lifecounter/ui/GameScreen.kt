package com.mtg.lifecounter.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtg.lifecounter.GameViewModel
import com.mtg.lifecounter.ui.theme.DarkBackground
import com.mtg.lifecounter.ui.theme.Player1Color
import com.mtg.lifecounter.ui.theme.Player2Color
import kotlinx.coroutines.delay

private const val IDLE_TIMEOUT_MS = 60_000L

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val activePlayer = viewModel.activePlayer
    val activeIndex = viewModel.activePlayerIndex
    val playerColor = if (activeIndex == 0) Player1Color else Player2Color

    var showResetDialog by remember { mutableStateOf(false) }
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

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .systemBarsPadding()
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            PlayerBar(
                players = viewModel.players,
                activeIndex = activeIndex,
                onPlayerSelected = { viewModel.switchPlayer(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            LifeGrid(
                lifeBase = activePlayer.lifeBase,
                currentLife = activePlayer.life,
                playerColor = playerColor,
                onLifeSelected = { viewModel.setLife(it) },
                onScrollPage = { viewModel.scrollLifeBase(it) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            BottomControls(
                poison = activePlayer.poison,
                onPoisonChange = { viewModel.adjustPoison(it) },
                diceValue = viewModel.diceValue,
                isDiceRolling = viewModel.isDiceRolling,
                onRollDice = { viewModel.rollDice() },
                onReset = { showResetDialog = true }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

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
                    fontFamily = FontFamily.Serif,
                    color = Color.Gray
                )
            }
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset Game") },
                text = { Text("Reset all life totals and poison counters?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.resetGame()
                        showResetDialog = false
                    }) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
