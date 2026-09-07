package com.mtg.lifecounter.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtg.lifecounter.PlayerData
import com.mtg.lifecounter.ui.theme.GoldAccent
import com.mtg.lifecounter.ui.theme.OnSurfaceDark
import com.mtg.lifecounter.ui.theme.Player1Color
import com.mtg.lifecounter.ui.theme.Player2Color
import com.mtg.lifecounter.ui.theme.PoisonGreen
import com.mtg.lifecounter.ui.theme.SurfaceDark

@Composable
fun PlayerBar(
    players: List<PlayerData>,
    activeIndex: Int,
    onPlayerSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        players.forEachIndexed { index, player ->
            val isActive = index == activeIndex
            val playerColor = if (index == 0) Player1Color else Player2Color

            Card(
                modifier = Modifier.weight(1f),
                onClick = { onPlayerSelected(index) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) playerColor else SurfaceDark
                ),
                border = if (isActive) {
                    BorderStroke(2.dp, GoldAccent)
                } else {
                    BorderStroke(1.dp, playerColor.copy(alpha = 0.3f))
                }
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Player ${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isActive) Color.White else OnSurfaceDark.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "\u2665 ${player.life}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) Color.White else OnSurfaceDark.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun BottomControls(
    poison: Int,
    onPoisonChange: (Int) -> Unit,
    diceValue: Int,
    isDiceRolling: Boolean,
    onRollDice: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PoisonCounter(
            poison = poison,
            onPoisonChange = onPoisonChange,
            modifier = Modifier.weight(1f)
        )

        DiceView(
            value = diceValue,
            isRolling = isDiceRolling,
            onRoll = onRollDice,
            modifier = Modifier.size(72.dp)
        )

        Card(
            onClick = onReset,
            modifier = Modifier.size(72.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.3f))
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("\u21BA", fontSize = 28.sp, color = GoldAccent)
            }
        }
    }
}

@Composable
private fun PoisonCounter(
    poison: Int,
    onPoisonChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, PoisonGreen.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text("\u2620", fontSize = 24.sp)
            Spacer(Modifier.width(8.dp))

            FilledIconButton(
                onClick = { onPoisonChange(-1) },
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = PoisonGreen.copy(alpha = 0.2f)
                )
            ) {
                Text(
                    "\u2212",
                    color = PoisonGreen,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = poison.toString(),
                modifier = Modifier.widthIn(min = 40.dp),
                textAlign = TextAlign.Center,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PoisonGreen
            )

            FilledIconButton(
                onClick = { onPoisonChange(1) },
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = PoisonGreen.copy(alpha = 0.2f)
                )
            ) {
                Text(
                    "+",
                    color = PoisonGreen,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DiceView(
    value: Int,
    isRolling: Boolean,
    onRoll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dice")
    val shake by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(50, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shake"
    )

    Card(
        modifier = modifier.graphicsLayer {
            if (isRolling) rotationZ = shake
        },
        onClick = onRoll,
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(
            width = if (isRolling) 2.dp else 1.dp,
            color = if (isRolling) GoldAccent else GoldAccent.copy(alpha = 0.5f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            DiceFace(value = value)
        }
    }
}

@Composable
private fun DiceFace(value: Int) {
    Canvas(modifier = Modifier
        .fillMaxSize()
        .padding(14.dp)) {
        val dotRadius = size.minDimension / 8f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val off = size.minDimension / 3f

        val dots: List<Offset> = when (value) {
            1 -> listOf(Offset(cx, cy))
            2 -> listOf(
                Offset(cx - off, cy - off),
                Offset(cx + off, cy + off)
            )
            3 -> listOf(
                Offset(cx - off, cy - off),
                Offset(cx, cy),
                Offset(cx + off, cy + off)
            )
            4 -> listOf(
                Offset(cx - off, cy - off),
                Offset(cx + off, cy - off),
                Offset(cx - off, cy + off),
                Offset(cx + off, cy + off)
            )
            5 -> listOf(
                Offset(cx - off, cy - off),
                Offset(cx + off, cy - off),
                Offset(cx, cy),
                Offset(cx - off, cy + off),
                Offset(cx + off, cy + off)
            )
            6 -> listOf(
                Offset(cx - off, cy - off),
                Offset(cx + off, cy - off),
                Offset(cx - off, cy),
                Offset(cx + off, cy),
                Offset(cx - off, cy + off),
                Offset(cx + off, cy + off)
            )
            else -> emptyList()
        }

        dots.forEach { dot ->
            drawCircle(
                color = Color.White,
                radius = dotRadius,
                center = dot
            )
        }
    }
}
