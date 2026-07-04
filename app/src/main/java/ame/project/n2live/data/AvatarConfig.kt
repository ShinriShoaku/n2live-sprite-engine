package ame.project.n2live.data

/**
 * Satu "state" animasi (idle, talk, angry, dst) milik sebuah layer.
 * frames berisi NAMA FILE PNG.
 * posX, posY, scale, zOrder bisa di-override per frame (null = pakai default LayerConfig).
 */
data class StateConfig(
    var loop: Boolean = true,
    var fps: Int = 8,
    var frames: MutableList<String> = mutableListOf(),
    var posX: MutableList<Float?> = mutableListOf(),
    var posY: MutableList<Float?> = mutableListOf(),
    var scale: MutableList<Float?> = mutableListOf(),
    var zOrder: MutableList<Int?> = mutableListOf()
)

/**
 * Satu layer visual.
 */
data class LayerConfig(
    var name: String,
    var zOrder: Int,
    var posX: Float = 0f,
    var posY: Float = 0f,
    var scale: Float = 1.0f,
    var states: MutableMap<String, StateConfig> = mutableMapOf()
)

data class AvatarConfig(
    var avatarName: String = "Mailin",
    var layers: MutableList<LayerConfig> = mutableListOf()
) {
    companion object {
        const val DEFAULT_STATE = "idle"
    }
}
