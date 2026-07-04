package ame.project.n2live.data

import android.content.Context
import com.google.gson.Gson
import java.io.File

object ConfigManager {
    private const val FILE_NAME = "config.json"
    private const val SPRITE_DIR = "sprites"

    fun spriteDir(context: Context): File {
        val dir = File(context.filesDir, SPRITE_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun load(context: Context): AvatarConfig {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return AvatarConfig()
        return try {
            val json = file.readText()
            Gson().fromJson(json, AvatarConfig::class.java) ?: AvatarConfig()
        } catch (e: Exception) {
            AvatarConfig()
        }
    }

    fun save(context: Context, config: AvatarConfig) {
        val file = File(context.filesDir, FILE_NAME)
        val json = Gson().toJson(config)
        file.writeText(json)
    }
}
