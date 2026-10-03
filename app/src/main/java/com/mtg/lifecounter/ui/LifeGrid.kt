package com.mtg.lifecounter.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.unit.sp
import com.mtg.lifecounter.R
import com.mtg.lifecounter.ui.theme.CardNumbersBorderColor
import com.mtg.lifecounter.ui.theme.CardNumbersColor
import com.mtg.lifecounter.ui.theme.GaramondFamily
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

private const val ROWS = 5
private const val COLS = 4

/** Highest life value the grid scrolls to; it is shown in the top left cell at most. */
const val MAX_LIFE = 200

/** Largest lifeBase: the top row then shows MAX_LIFE - 3 .. MAX_LIFE. */
const val MAX_LIFE_BASE = MAX_LIFE - COLS * ROWS

/** Number of cell rows drawn in Field.png; one row of it is tiled under every grid row. */
private const val FIELD_IMAGE_ROWS = 10

/** Corner radius of the field relative to the cell width. */
private const val FIELD_CORNER_RATIO = 0.15f

/**
 * Life grid with a marble on the current life value.
 *
 * Swiping down scrolls the grid down and adds higher rows on top, swiping up scrolls it up
 * and adds lower rows at the bottom; the bottom row never goes below 1..4. When the finger
 * is lifted the grid settles on a whole row and reports the new [lifeBase].
 */
@Composable
fun LifeGrid(
    lifeBase: Int,
    currentLife: Int,
    marbleRes: Int,
    onLifeSelected: (Int) -> Unit,
    onLifeBaseChange: (Int) -> Unit,
    marbleSize: Dp,
    modifier: Modifier = Modifier,
    marbleKey: Any? = null,
    marbleModifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val gridW = maxWidth
        val cellW = gridW / COLS
        val cellH = maxHeight / ROWS
        val gridWPx = constraints.maxWidth.toFloat()
        val gridHPx = constraints.maxHeight.toFloat()
        val cellWPx = gridWPx / COLS
        val cellHPx = gridHPx / ROWS

        val scope = rememberCoroutineScope()
        val currentOnLifeSelected by rememberUpdatedState(onLifeSelected)
        val currentOnLifeBaseChange by rememberUpdatedState(onLifeBaseChange)
        val latestLife by rememberUpdatedState(currentLife)

        // How far the rows are scrolled down, in pixels; 0 keeps 1..4 in the bottom row
        val maxScrollPx = MAX_LIFE_BASE / COLS * cellHPx
        var scrollPx by remember(marbleKey, cellHPx) {
            mutableFloatStateOf((lifeBase / COLS * cellHPx).coerceIn(0f, maxScrollPx))
        }
        var settleJob by remember { mutableStateOf<Job?>(null) }
        var isScrolling by remember { mutableStateOf(false) }

        /** Center of the cell with [life] in unscrolled grid pixels. */
        fun cellCenter(life: Int): Offset {
            val row = Math.floorDiv(life - 1, COLS)
            val col = COLS - 1 - Math.floorMod(life - 1, COLS)
            return Offset(cellWPx * (col + 0.5f), gridHPx - cellHPx * (row + 0.5f))
        }

        /** Life value of the cell under [pos] given in on-screen grid pixels. */
        fun lifeAt(pos: Offset): Int {
            val row = floor((gridHPx - pos.y + scrollPx) / cellHPx).toInt().coerceAtLeast(0)
            val col = (pos.x / cellWPx).toInt().coerceIn(0, COLS - 1)
            return row * COLS + (COLS - col)
        }

        // Keeps the marble inside the field, as cropMarblePos in the iOS version
        fun crop(pos: Offset) = Offset(
            pos.x.coerceIn(cellWPx / 2f, gridWPx - cellWPx / 2f),
            pos.y.coerceIn(cellHPx / 2.5f, gridHPx - cellHPx / 2.5f)
        )

        fun settle() {
            isScrolling = false
            val rows = (scrollPx / cellHPx).roundToInt().coerceIn(0, MAX_LIFE_BASE / COLS)
            currentOnLifeBaseChange(rows * COLS)
            settleJob = scope.launch {
                animate(scrollPx, rows * cellHPx, animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)) { value, _ ->
                    scrollPx = value.coerceIn(0f, maxScrollPx)
                }
            }
        }

        // Field.png background row (brown cells with borders)
        val field = ImageBitmap.imageResource(R.drawable.field)
        val rowPainter = remember(field) {
            BitmapPainter(field, srcSize = IntSize(field.width, field.height / FIELD_IMAGE_ROWS))
        }

        // Rows of cells with numbers; one extra row is drawn to fill the gap while scrolling
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cellW * FIELD_CORNER_RATIO))
                .pointerInput(cellHPx, marbleKey) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            settleJob?.cancel()
                            isScrolling = true
                        },
                        onDragEnd = { settle() },
                        onDragCancel = { settle() },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            scrollPx = (scrollPx + dragAmount).coerceIn(0f, maxScrollPx)

                            // The marble rides with the grid but stays on the field; held at an
                            // edge it picks up the value of the cell under it
                            val life = lifeAt(crop(cellCenter(latestLife) + Offset(0f, scrollPx)))
                            if (life != latestLife) currentOnLifeSelected(life)
                        }
                    )
                }
        ) {
            val bottomRow = floor(scrollPx / cellHPx).toInt()
            val shift = scrollPx - bottomRow * cellHPx
            for (i in 0..ROWS) {
                val rowIndex = bottomRow + i
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, (gridHPx - (i + 1) * cellHPx + shift).roundToInt()) }
                        .size(gridW, cellH)
                ) {
                    Image(
                        painter = rowPainter,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                    Row(modifier = Modifier.fillMaxSize()) {
                        for (col in 0 until COLS) {
                            val lifeValue = rowIndex * COLS + (COLS - col)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
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
        }

        // Marble overlay on the selected life cell; always kept on the field
        val target = cellCenter(currentLife)
        run {
            // Marble center in unscrolled grid pixels; follows the finger while dragged,
            // springs to a cell otherwise. A new marbleKey places it at its cell without sliding.
            val marbleCenter = remember(marbleKey) { Animatable(target, Offset.VectorConverter) }
            var dragCenter by remember(marbleKey) { mutableStateOf<Offset?>(null) }
            val isDragging = dragCenter != null

            LaunchedEffect(marbleCenter, target, isDragging, isScrolling) {
                if (isDragging) return@LaunchedEffect
                if (isScrolling) marbleCenter.snapTo(target)
                else marbleCenter.animateTo(target, spring(dampingRatio = 0.7f, stiffness = 300f))
            }

            Box(
                modifier = Modifier
                    .offset {
                        val center = dragCenter ?: crop(marbleCenter.value + Offset(0f, scrollPx))
                        val half = marbleSize.toPx() / 2f
                        IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt())
                    }
                    .size(marbleSize)
                    .then(marbleModifier)
                    .pointerInput(cellWPx, cellHPx, marbleCenter) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            var center = crop(marbleCenter.value + Offset(0f, scrollPx))
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

                            currentOnLifeSelected(lifeAt(center))
                            val dropped = center - Offset(0f, scrollPx)
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
