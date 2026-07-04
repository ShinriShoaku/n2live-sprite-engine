package ame.project.n2live.gl

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import ame.project.n2live.data.AvatarConfig

/**
 * GLSurfaceView kustom untuk sprite. Latar transparan (RGBA 8888) supaya
 * karakter bisa "melayang" di atas UI lain (mis. overlay/live mode).
 *
 * fps: 60 untuk Live Mode, 30 cukup untuk Preview di Settings (hemat baterai/CPU).
 */
class SpriteGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    lateinit var spriteRenderer: SpriteRenderer
        private set

    fun init(config: AvatarConfig, fps: Int = 60) {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        setZOrderMediaOverlay(true)

        spriteRenderer = SpriteRenderer(context, config)
        setRenderer(spriteRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY

        // Frame skipping: batasi refresh dengan mengatur render interval manual
        // via post-delay tidak diperlukan karena kita pakai CONTINUOUSLY + vsync,
        // tapi untuk device tanpa vsync stabil, gunakan setFrameRate (API 30+) pada Surface
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            try {
                holder.surface.setFrameRate(fps.toFloat(), android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
            } catch (_: Exception) { /* tidak semua device mendukung */ }
        }
    }

    fun updateConfig(config: AvatarConfig) {
        if (::spriteRenderer.isInitialized) {
            queueEvent { spriteRenderer.setConfig(config) }
        }
    }

    fun setManualTime(sec: Float) {
        if (::spriteRenderer.isInitialized) {
            queueEvent { spriteRenderer.setManualTime(sec) }
        }
    }

    fun setState(stateName: String, forceReset: Boolean = false) {
        if (::spriteRenderer.isInitialized) {
            queueEvent { spriteRenderer.setState(stateName, forceReset) }
        }
    }

    fun getElapsedSec(): Float {
        return if (::spriteRenderer.isInitialized) spriteRenderer.getElapsedSec() else 0f
    }
}
