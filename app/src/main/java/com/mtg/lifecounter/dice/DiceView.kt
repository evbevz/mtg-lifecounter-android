package com.mtg.lifecounter.dice

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.opengl.EGL14
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import kotlin.math.roundToInt

/** Bridge between the Compose game screen and the dice layer. Coordinates are root pixels. */
interface DiceController {
    fun setGameArea(left: Float, top: Float, width: Float, height: Float)
    fun setDefaultPlace(x: Float, y: Float)
    fun setMarbles(marbles: List<DiceMarble>)
    fun returnToDefaultPlace()
    fun setDiceVisible(visible: Boolean)
    var onUserInteraction: (() -> Unit)?
}

/**
 * Transparent OpenGL ES 2 layer with the 3D dice, placed above the Compose content.
 * Touches that miss the dice are not consumed and reach the views below.
 */
@SuppressLint("ViewConstructor")
class DiceView(context: Context) : GLSurfaceView(context), DiceController {

    private val engine = DiceEngine(resources.displayMetrics.density)

    private var areaLeft = 0f
    private var areaTop = 0f
    private var defaultPlace: Pair<Float, Float>? = null
    private var marbles: List<DiceMarble> = emptyList()

    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    override var onUserInteraction: (() -> Unit)? = null

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(DiceConfigChooser())
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
        preserveEGLContextOnPause = true
        setRenderer(DiceRenderer(context, this, engine))
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun setGameArea(left: Float, top: Float, width: Float, height: Float) {
        val params = (layoutParams as? FrameLayout.LayoutParams) ?: FrameLayout.LayoutParams(0, 0)
        val newWidth = width.roundToInt()
        val newHeight = height.roundToInt()
        val newLeft = left.roundToInt()
        val newTop = top.roundToInt()
        if (params.width != newWidth || params.height != newHeight ||
            params.leftMargin != newLeft || params.topMargin != newTop
        ) {
            params.width = newWidth
            params.height = newHeight
            params.leftMargin = newLeft
            params.topMargin = newTop
            layoutParams = params
        }
        areaLeft = newLeft.toFloat()
        areaTop = newTop.toFloat()
        pushLayout()
    }

    override fun setDefaultPlace(x: Float, y: Float) {
        defaultPlace = x to y
        pushLayout()
    }

    override fun setMarbles(marbles: List<DiceMarble>) {
        this.marbles = marbles
        pushLayout()
    }

    override fun returnToDefaultPlace() {
        engine.returnToDefaultPlace()
        requestRender()
    }

    override fun setDiceVisible(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        engine.setViewSize(w.toFloat(), h.toFloat())
        requestRender()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val time = event.eventTime / 1000.0
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!engine.touchDown(event.x, event.y, time)) return false
                activePointerId = event.getPointerId(0)
                lastTouchX = event.x
                lastTouchY = event.y
                onUserInteraction?.invoke()
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) return true
                val x = event.getX(index)
                val y = event.getY(index)
                engine.touchMove(x, y, lastTouchX, lastTouchY, time)
                lastTouchX = x
                lastTouchY = y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) != activePointerId) return true
                engine.touchUp(event.getX(event.actionIndex), event.getY(event.actionIndex), time)
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
            MotionEvent.ACTION_CANCEL -> {
                engine.touchCancel()
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
        requestRender()
        return true
    }

    private fun pushLayout() {
        defaultPlace?.let { (x, y) -> engine.setDefaultPlace(x - areaLeft, y - areaTop) }
        engine.setMarbles(marbles.map { it.copy(x = it.x - areaLeft, y = it.y - areaTop) })
        requestRender()
    }
}

/** RGBA8888 with a 16-bit depth buffer, 4x multisampling when available. */
private class DiceConfigChooser : GLSurfaceView.EGLConfigChooser {

    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig =
        choose(egl, display, samples = 4)
            ?: choose(egl, display, samples = 0)
            ?: throw IllegalStateException("No suitable EGL config for the dice view")

    private fun choose(egl: EGL10, display: EGLDisplay, samples: Int): EGLConfig? {
        val attributes = buildList {
            addAll(listOf(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8))
            addAll(listOf(EGL10.EGL_ALPHA_SIZE, 8, EGL10.EGL_DEPTH_SIZE, 16))
            addAll(listOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT))
            if (samples > 0) addAll(listOf(EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, samples))
            add(EGL10.EGL_NONE)
        }.toIntArray()

        val count = IntArray(1)
        if (!egl.eglChooseConfig(display, attributes, null, 0, count) || count[0] <= 0) return null
        val configs = arrayOfNulls<EGLConfig>(count[0])
        if (!egl.eglChooseConfig(display, attributes, configs, configs.size, count)) return null

        val value = IntArray(1)
        fun EGLConfig.attribute(name: Int): Int =
            if (egl.eglGetConfigAttrib(display, this, name, value)) value[0] else 0

        return configs.filterNotNull().firstOrNull { config ->
            config.attribute(EGL10.EGL_RED_SIZE) == 8 && config.attribute(EGL10.EGL_GREEN_SIZE) == 8 &&
                config.attribute(EGL10.EGL_BLUE_SIZE) == 8 && config.attribute(EGL10.EGL_ALPHA_SIZE) == 8
        } ?: configs.firstOrNull()
    }
}
