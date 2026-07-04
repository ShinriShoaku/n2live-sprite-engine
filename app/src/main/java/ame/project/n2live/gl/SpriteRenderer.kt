package ame.project.n2live.gl

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import ame.project.n2live.data.AvatarConfig
import ame.project.n2live.data.ConfigManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Renderer utama. Membaca AvatarConfig (layer + state + frame), menghitung frame
 * mana yang harus ditampilkan berdasarkan waktu berjalan, lalu menggambar tiap
 * layer sebagai quad full-canvas dari bawah (z_order kecil) ke atas.
 *
 * Vertex/UV buffer dibuat SEKALI di onSurfaceCreated dan disimpan di VBO -
 * tidak di-recalculate tiap frame (sesuai strategi "ringan" di spek).
 */
class SpriteRenderer(
    context: Context,
    private var config: AvatarConfig
) : GLSurfaceView.Renderer {

    interface StateListener {
        fun onStateFinished(stateName: String)
    }

    var listener: StateListener? = null

    private val appContext = context.applicationContext
    private lateinit var textureLoader: TextureLoader

    private var program = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var mvpMatrixHandle = 0
    private var textureUniformHandle = 0

    private var vertexBuffer: FloatBuffer? = null
    private var texCoordBuffer: FloatBuffer? = null

    private val projectionMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var viewWidth = 1
    private var viewHeight = 1

    // state saat ini yang aktif untuk SEMUA layer (idle/talk/angry, dst)
    @Volatile private var currentState: String = AvatarConfig.DEFAULT_STATE
    private var stateStartTimeMs: Long = SystemClock.elapsedRealtime()
    private var lastReportedFinished = false

    private var manualTimeSec: Float = 0f // Start paused at 0.0s by default in Settings preview

    fun getElapsedSec(): Float {
        val currentTime = SystemClock.elapsedRealtime()
        return if (manualTimeSec >= 0) manualTimeSec 
               else (currentTime - stateStartTimeMs) / 1000f
    }

    fun setManualTime(sec: Float) {
        val currentElapsedTime = if (manualTimeSec >= 0) manualTimeSec else (SystemClock.elapsedRealtime() - stateStartTimeMs) / 1000f
        
        if (manualTimeSec < 0 && sec >= 0) {
            // Switching from Auto to Manual: currentElapsedTime is already calculated
        } else if (manualTimeSec >= 0 && sec < 0) {
            // Switching from Manual to Auto: start from where we paused
            stateStartTimeMs = SystemClock.elapsedRealtime() - (currentElapsedTime * 1000).toLong()
        }
        manualTimeSec = sec
    }

    fun setConfig(newConfig: AvatarConfig) {
        config = newConfig
    }

    /** Panggil dari luar (mis. dari hasil AI: "mulut terbuka" -> state "talk"). */
    fun setState(stateName: String, forceReset: Boolean = false) {
        if (!forceReset && currentState == stateName) return
        currentState = stateName
        stateStartTimeMs = SystemClock.elapsedRealtime()
        lastReportedFinished = false
    }

    fun getState(): String = currentState

    override fun onSurfaceCreated(gl: GL10?, eglConfig: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        program = ShaderUtil.buildProgram()
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        textureUniformHandle = GLES20.glGetUniformLocation(program, "uTexture")

        buildQuadBuffers()
        textureLoader = TextureLoader(ConfigManager.spriteDir(appContext))
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        GLES20.glViewport(0, 0, width, height)
        // Ortho projection sederhana: -1..1 di kedua sumbu (quad full-canvas)
        Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -1f, 1f, -1f, 1f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (config.layers.isEmpty()) return

        GLES20.glUseProgram(program)

        vertexBuffer?.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        texCoordBuffer?.position(0)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        val currentTime = SystemClock.elapsedRealtime()
        var elapsedSec = if (manualTimeSec >= 0) manualTimeSec 
                         else (currentTime - stateStartTimeMs) / 1000f
        
        var anyLoopingOrUnfinished = false
        
        // Cek loop dari layer pertama sebagai referensi durasi total
        val refLayer = config.layers.firstOrNull()
        val refState = refLayer?.states?.get(currentState) ?: refLayer?.states?.get(AvatarConfig.DEFAULT_STATE)
        if (refState != null && refState.frames.isNotEmpty()) {
            val totalDuration = refState.frames.size.toFloat() / refState.fps
            if (totalDuration > 0) {
                if (refState.loop) {
                    elapsedSec %= totalDuration
                } else if (elapsedSec > totalDuration) {
                    elapsedSec = totalDuration
                }
            }
        }

        android.util.Log.d("N2Live_GL", "Render: state=$currentState, elapsed=$elapsedSec, manual=$manualTimeSec")

        // Sort layers dynamically
        val sortedLayers = config.layers.sortedBy { layer ->
            val state = layer.states[currentState] ?: layer.states[AvatarConfig.DEFAULT_STATE]
            val fIndex = if (state != null) {
                val rawIndex = (elapsedSec * state.fps).toInt()
                if (state.loop) {
                    if (state.frames.isEmpty()) 0 else rawIndex % state.frames.size
                } else {
                    minOf(rawIndex, maxOf(0, state.frames.size - 1))
                }
            } else 0
            
            // Safety check for null lists (Gson can leave them null)
            state?.zOrder?.getOrNull(fIndex) ?: layer.zOrder
        }

        for (layer in sortedLayers) {
            val state = layer.states[currentState] ?: layer.states[AvatarConfig.DEFAULT_STATE] ?: continue
            if (state.frames.isEmpty()) continue

            var frameIndex = 0
            val frameFile = resolveFrame(state, elapsedSec, onFinished = { _ -> }, onIndex = { index -> frameIndex = index })

            if (frameFile.isEmpty()) continue

            // Use per-frame values if available, otherwise fallback to layer defaults
            val pX = state.posX?.getOrNull(frameIndex) ?: layer.posX
            val pY = state.posY?.getOrNull(frameIndex) ?: layer.posY
            val sc = state.scale?.getOrNull(frameIndex) ?: layer.scale

            // Apply translation and scale
            Matrix.setIdentityM(mvpMatrix, 0)
            Matrix.translateM(mvpMatrix, 0, pX, pY, 0f)
            Matrix.scaleM(mvpMatrix, 0, sc, sc, 1f)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

            if (frameFile.isEmpty()) continue
            val textureId = textureLoader.getTexture(frameFile)
            if (textureId == 0) continue

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glUniform1i(textureUniformHandle, 0)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        // Non-looping state (mis. "angry") selesai -> laporkan sekali ke listener
        if (!anyLoopingOrUnfinished && !lastReportedFinished) {
            lastReportedFinished = true
            listener?.onStateFinished(currentState)
        }

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    /** Hitung nama file frame yang aktif berdasarkan waktu berjalan & fps. */
    private fun resolveFrame(
        state: ame.project.n2live.data.StateConfig,
        elapsedSec: Float,
        onFinished: (Boolean) -> Unit,
        onIndex: (Int) -> Unit
    ): String {
        val rawIndex = (elapsedSec * state.fps).toInt()
        val index = if (state.loop) {
            if (state.frames.isEmpty()) 0 else rawIndex % state.frames.size
        } else {
            minOf(rawIndex, state.frames.size - 1)
        }
        onIndex(index)
        onFinished(!state.loop && rawIndex >= state.frames.size - 1)
        return if (state.frames.isEmpty()) "" else state.frames[index]
    }

    private fun buildQuadBuffers() {
        // Quad full-canvas, TRIANGLE_STRIP: (-1,1) (-1,-1) (1,1) (1,-1)
        val vertices = floatArrayOf(
            -1f, 1f,
            -1f, -1f,
            1f, 1f,
            1f, -1f
        )
        // UV: origin kiri-atas pada bitmap Android -> flip Y
        val texCoords = floatArrayOf(
            0f, 0f,
            0f, 1f,
            1f, 0f,
            1f, 1f
        )

        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(vertices); position(0)
            }
        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(texCoords); position(0)
            }
    }

    fun release() {
        if (::textureLoader.isInitialized) textureLoader.releaseAll()
    }
}
