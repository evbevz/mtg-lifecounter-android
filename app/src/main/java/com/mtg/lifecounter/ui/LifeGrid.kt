package com.mtg.lifecounter.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.mtg.lifecounter.R
import com.mtg.lifecounter.ui.theme.CardNumbersBorderColor
import com.mtg.lifecounter.ui.theme.CardNumbersColor
import com.mtg.lifecounter.ui.theme.GaramondFamily
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val ROWS = 5
private const val COLS = 4
private const val SCROLL_THRESHOLD = 200f
private const val PAGE_SIZE = COLS * ROWS

/** Number of cell rows drawn in Field.png; only the top [ROWS] of them are shown. */
private const val FIELD_IMAGE_ROWS = 10

@Composable
fun LifeGrid(
    lifeBase: Int,
    currentLife: Int,
    marbleRes: Int,
    onLifeSelected: (Int) -> Unit,
    onScrollPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    marbleModifier: Modifier = Modifier
) {
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier
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
    ) {
        val gridW = maxWidth
        val gridH = maxHeight
        val cellW = gridW / COLS
        val cellH = gridH / ROWS

        // Field.png background texture (brown grid with cell borders)
        val field = ImageBitmap.imageResource(R.drawable.field)
        val fieldPainter = remember(field) {
            BitmapPainter(field, srcSize = IntSize(field.width, field.height * ROWS / FIELD_IMAGE_ROWS))
        }
        Image(
            painter = fieldPainter,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )

        // Number labels
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

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { onLifeSelected(lifeValue) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = lifeValue.toString(),
                                color = CardNumbersColor,
                                fontFamily = GaramondFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = dynamicFontSize(cellW, lifeValue),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Marble overlay on the selected life cell
        val selectedIndex = lifeBase + COLS * ROWS - currentLife
        val selectedCol = selectedIndex % COLS
        val selectedRow = selectedIndex / COLS
        val isVisible = selectedRow in 0 until ROWS && selectedCol in 0 until COLS

        if (isVisible) {
            val marbleSize = min(cellW, cellH) * 1.6f
            val cellWPx = constraints.maxWidth.toFloat() / COLS
            val cellHPx = constraints.maxHeight.toFloat() / ROWS
            val gridWPx = constraints.maxWidth.toFloat()
            val gridHPx = constraints.maxHeight.toFloat()
            val target = Offset(cellWPx * (selectedCol + 0.5f), cellHPx * (selectedRow + 0.5f))

            // Marble center in grid pixels; follows the finger while dragged, springs to a cell otherwise
            val marbleCenter = remember { Animatable(target, Offset.VectorConverter) }
            var dragCenter by remember { mutableStateOf<Offset?>(null) }
            val isDragging = dragCenter != null
            val scope = rememberCoroutineScope()
            val currentOnLifeSelected by rememberUpdatedState(onLifeSelected)
            val currentLifeBase by rememberUpdatedState(lifeBase)

            LaunchedEffect(target, isDragging) {
                if (!isDragging) {
                    marbleCenter.animateTo(target, spring(dampingRatio = 0.7f, stiffness = 300f))
                }
            }

            // Keeps the marble inside the field, as cropMarblePos in the iOS version
            fun crop(pos: Offset) = Offset(
                pos.x.coerceIn(cellWPx / 2f, gridWPx - cellWPx / 2f),
                pos.y.coerceIn(cellHPx / 2.5f, gridHPx - cellHPx / 2.5f)
            )

            Box(
                modifier = Modifier
                    .offset {
                        val center = dragCenter ?: marbleCenter.value
                        val half = marbleSize.toPx() / 2f
                        IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt())
                    }
                    .size(marbleSize)
                    .then(marbleModifier)
                    .pointerInput(cellWPx, cellHPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            var center = marbleCenter.value
                            dragCenter = center

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                center = crop(center + change.positionChange())
                                dragCenter = center
                                change.consume()
                            }

                            val col = (center.x / cellWPx).toInt().coerceIn(0, COLS - 1)
                            val row = (center.y / cellHPx).toInt().coerceIn(0, ROWS - 1)
                            currentOnLifeSelected(currentLifeBase + COLS * (ROWS - row) - col)
                            val dropped = center
                            scope.launch {
                                marbleCenter.snapTo(dropped)
                                dragCenter = null
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(marbleRes),
                    contentDescription = "Life: $currentLife",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = currentLife.toString(),
                    fontFamily = GaramondFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (marbleSize.value * 0.3f).sp,
                    color = CardNumbersBorderColor.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Adjust font size for large numbers so they fit within cells.
 * Matches the iOS behavior of shrinking font for 3+ digit numbers.
 */
private fun dynamicFontSize(cellWidth: Dp, value: Int): androidx.compose.ui.unit.TextUnit {
    val base = cellWidth.value * 0.42f
    return when {
        value > 999 || value < -99 -> (base * 0.6f).sp
        value > 99 || value < -9   -> (base * 0.75f).sp
        else                       -> base.sp
    }
}
