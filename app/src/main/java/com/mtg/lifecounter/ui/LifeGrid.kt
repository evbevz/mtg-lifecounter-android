package com.mtg.lifecounter.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtg.lifecounter.ui.theme.CardBackground
import com.mtg.lifecounter.ui.theme.CardBorder
import com.mtg.lifecounter.ui.theme.CardNumberColor

private const val ROWS = 10
private const val COLS = 4
private const val SCROLL_THRESHOLD = 200f
private const val PAGE_SIZE = 20

@Composable
fun LifeGrid(
    lifeBase: Int,
    currentLife: Int,
    playerColor: Color,
    onLifeSelected: (Int) -> Unit,
    onScrollPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .border(2.dp, CardBorder, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        dragAccumulator += dragAmount
                        if (dragAccumulator > SCROLL_THRESHOLD) {
                            onScrollPage(-PAGE_SIZE)
                            dragAccumulator = 0f
                        } else if (dragAccumulator < -SCROLL_THRESHOLD) {
                            onScrollPage(PAGE_SIZE)
                            dragAccumulator = 0f
                        }
                    }
                )
            }
            .padding(6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            for (row in 0 until ROWS) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (col in 0 until COLS) {
                        val lifeValue = lifeBase + (COLS * (ROWS - row)) - col
                        val isSelected = lifeValue == currentLife

                        LifeCell(
                            value = lifeValue,
                            isSelected = isSelected,
                            playerColor = playerColor,
                            onClick = { onLifeSelected(lifeValue) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LifeCell(
    value: Int,
    isSelected: Boolean,
    playerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) playerColor else Color.Transparent,
        animationSpec = tween(200),
        label = "cellBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else CardNumberColor,
        animationSpec = tween(200),
        label = "cellText"
    )

    Box(
        modifier = modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value.toString(),
            color = textColor,
            fontSize = 18.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center
        )
    }
}
