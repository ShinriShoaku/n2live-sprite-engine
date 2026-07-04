package ame.project.n2live.gl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import java.io.File

/**
 * Loader + cache texture OpenGL. Setiap file PNG hanya di-upload sekali ke GPU
 * (dipanggil dari GL thread), lalu textureId-nya dipakai berulang saat re-render.
 *
 * CATATAN OPTIMASI: untuk produksi, gabungkan semua PNG jadi satu Texture Atlas
 * (mis. dengan TexturePacker) agar hanya 1 texture bind per draw call & 1 file
 * yang perlu dimuat ke GPU. Versi ini memuat tiap PNG sebagai texture terpisah
 * supaya arsitekturnya sederhana dan mudah dikembangkan; migrasi ke atlas cukup
 * mengubah TextureLoader + cara hitung UV di SpriteRenderer.
 */
class TextureLoader(private val spriteDir: File) {

    // nama file -> textureId GPU
    private val cache = HashMap<String, Int>()

    /** Harus dipanggil dari GL thread (mis. dalam onDrawFrame / onSurfaceCreated). */
    fun getTexture(fileName: String): Int {
        cache[fileName]?.let { return it }

        val file = File(spriteDir, fileName)
        if (!file.exists()) return 0

        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return 0
        val textureId = uploadBitmap(bitmap)
        bitmap.recycle()

        cache[fileName] = textureId
        return textureId
    }

    private fun uploadBitmap(bitmap: Bitmap): Int {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        val textureId = textureIds[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        return textureId
    }

    fun releaseAll() {
        if (cache.isEmpty()) return
        val ids = cache.values.toIntArray()
        GLES20.glDeleteTextures(ids.size, ids, 0)
        cache.clear()
    }
}
