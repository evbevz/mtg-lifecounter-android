package com.mtg.lifecounter.dice

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private const val TAG = "DiceRenderer"

private const val MESH_ASSET = "dice_mesh.bin"
private const val FLOATS_PER_VERTEX = 6
private const val STRIDE = FLOATS_PER_VERTEX * 4

/** Material ranges of the casino cube model (CUBE_MTL.h). */
private class Material(val first: Int, val count: Int, val diffuse: FloatArray, val specular: FloatArray)

private val MATERIALS = arrayOf(
    // Black_plastic
    Material(0, 10080, floatArrayOf(0.1f, 0.1f, 0.1f), floatArrayOf(1f, 1f, 1f)),
    // Oro
    Material(10080, 18144, floatArrayOf(0f, 0f, 0f), floatArrayOf(0.8f, 0.6f, 0.2f)),
)

private const val VERTEX_SHADER = """
attribute vec3 aPosition;
attribute vec3 aNormal;

uniform mat4 uProjectionMatrix;
uniform mat4 uModelViewMatrix;
uniform mat3 uNormalMatrix;

varying vec3 vNormal;

void main() {
    vNormal = uNormalMatrix * aNormal;
    gl_Position = uProjectionMatrix * uModelViewMatrix * vec4(aPosition, 1.0);
}
"""

private const val FRAGMENT_SHADER = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

varying vec3 vNormal;

uniform vec3 uDiffuse;
uniform vec3 uSpecular;

void main() {
    vec3 ka = vec3(0.05);
    vec3 kd = uDiffuse;
    vec3 ks = uSpecular;
    float alpha = 1.0;

    vec3 L = normalize(vec3(1.0, 10.0, -10.0));
    vec3 N = normalize(vNormal);
    vec3 V = vec3(0.0, 0.0, 1.0);
    vec3 R = reflect(L, N);

    float df = max(0.0, dot(L, N));
    float sf = pow(max(0.0, dot(R, V)), alpha);

    gl_FragColor = vec4(ka + kd * df + ks * sf, 1.0);
}
"""

/** Draws the dice with the Phong shader of the iOS version over a transparent background. */
internal class DiceRenderer(
    private val context: Context,
    private val view: GLSurfaceView,
    private val engine: DiceEngine,
) : GLSurfaceView.Renderer {

    private val mesh by lazy { loadMesh() }

    private var program = 0
    private var vertexBuffer = 0
    private var uProjection = 0
    private var uModelView = 0
    private var uNormal = 0
    private var uDiffuse = 0
    private var uSpecular = 0

    private val projection = FloatArray(16)
    private val modelView = FloatArray(16)
    private val translation = FloatArray(16)
    private val inverse = FloatArray(16)
    private val normalMatrix4 = FloatArray(16)
    private val normalMatrix = FloatArray(9)
    private val transform = DiceTransform()

    private var lastFrameNanos = 0L
    private var idle = true
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var areaLeft = 0
    private var areaTop = 0
    private var areaWidth = 0
    private var areaHeight = 0

    /** The dice lives in the game box; the GL surface may be the full screen. */
    fun setGameArea(left: Float, top: Float, width: Float, height: Float) {
        areaLeft = left.toInt()
        areaTop = top.toInt()
        areaWidth = width.toInt()
        areaHeight = height.toInt()
        applyProjection()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = buildProgram()
        uProjection = GLES20.glGetUniformLocation(program, "uProjectionMatrix")
        uModelView = GLES20.glGetUniformLocation(program, "uModelViewMatrix")
        uNormal = GLES20.glGetUniformLocation(program, "uNormalMatrix")
        uDiffuse = GLES20.glGetUniformLocation(program, "uDiffuse")
        uSpecular = GLES20.glGetUniformLocation(program, "uSpecular")

        val buffers = IntArray(1)
        GLES20.glGenBuffers(1, buffers, 0)
        vertexBuffer = buffers[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBuffer)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mesh.capacity(), mesh, GLES20.GL_STATIC_DRAW)

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        idle = true
        view.requestRender()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        applyProjection()
        idle = true
        view.requestRender()
    }

    private fun applyProjection() {
        val width = if (areaWidth > 0) areaWidth else surfaceWidth
        val height = if (areaHeight > 0) areaHeight else surfaceHeight
        if (width <= 0 || height <= 0) return
        // GL origin is bottom-left; game-area top is in view coordinates
        val viewportY = (surfaceHeight - areaTop - height).coerceAtLeast(0)
        GLES20.glViewport(areaLeft.coerceAtLeast(0), viewportY, width, height)
        val halfWidth = DiceEngine.PV_WIDTH / 2f
        val halfHeight = halfWidth * height / width
        Matrix.frustumM(projection, 0, -halfWidth, halfWidth, -halfHeight, halfHeight, DiceEngine.NEAR, DiceEngine.FAR)
        engine.setViewSize(width.toFloat(), height.toFloat())
    }

    override fun onDrawFrame(gl: GL10?) {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return
        val now = System.nanoTime()
        val dt = if (idle) 1f / 60f else (now - lastFrameNanos) / 1e9f
        lastFrameNanos = now

        val active = engine.advance(dt, transform)
        idle = !active

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        Matrix.setIdentityM(translation, 0)
        Matrix.translateM(translation, 0, transform.x, transform.y, -DiceEngine.SHIFT_Z)
        Matrix.scaleM(translation, 0, DiceEngine.SCALE_FACTOR, DiceEngine.SCALE_FACTOR, DiceEngine.SCALE_FACTOR)
        Matrix.multiplyMM(modelView, 0, translation, 0, transform.rotation, 0)

        Matrix.invertM(inverse, 0, modelView, 0)
        Matrix.transposeM(normalMatrix4, 0, inverse, 0)
        for (col in 0 until 3) {
            for (row in 0 until 3) normalMatrix[col * 3 + row] = normalMatrix4[col * 4 + row]
        }

        GLES20.glUniformMatrix4fv(uProjection, 1, false, projection, 0)
        GLES20.glUniformMatrix4fv(uModelView, 1, false, modelView, 0)
        GLES20.glUniformMatrix3fv(uNormal, 1, false, normalMatrix, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBuffer)
        GLES20.glEnableVertexAttribArray(ATTRIB_POSITION)
        GLES20.glVertexAttribPointer(ATTRIB_POSITION, 3, GLES20.GL_FLOAT, false, STRIDE, 0)
        GLES20.glEnableVertexAttribArray(ATTRIB_NORMAL)
        GLES20.glVertexAttribPointer(ATTRIB_NORMAL, 3, GLES20.GL_FLOAT, false, STRIDE, 12)

        for (material in MATERIALS) {
            GLES20.glUniform3fv(uDiffuse, 1, material.diffuse, 0)
            GLES20.glUniform3fv(uSpecular, 1, material.specular, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, material.first, material.count)
        }

        if (active) view.requestRender()
    }

    private fun loadMesh(): ByteBuffer {
        val bytes = context.assets.open(MESH_ASSET).use { it.readBytes() }
        val source = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val count = source.int
        val floats = source.asFloatBuffer()
        floats.limit(count * FLOATS_PER_VERTEX)
        val data = ByteBuffer.allocateDirect(count * STRIDE).order(ByteOrder.nativeOrder())
        data.asFloatBuffer().put(floats)
        return data
    }

    private fun buildProgram(): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        val handle = GLES20.glCreateProgram()
        GLES20.glAttachShader(handle, vertexShader)
        GLES20.glAttachShader(handle, fragmentShader)
        GLES20.glBindAttribLocation(handle, ATTRIB_POSITION, "aPosition")
        GLES20.glBindAttribLocation(handle, ATTRIB_NORMAL, "aNormal")
        GLES20.glLinkProgram(handle)

        val status = IntArray(1)
        GLES20.glGetProgramiv(handle, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == GLES20.GL_FALSE) Log.e(TAG, "Program link error: ${GLES20.glGetProgramInfoLog(handle)}")

        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return handle
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == GLES20.GL_FALSE) Log.e(TAG, "Shader compile error: ${GLES20.glGetShaderInfoLog(shader)}")
        return shader
    }

    private companion object {
        const val ATTRIB_POSITION = 0
        const val ATTRIB_NORMAL = 1
    }
}
