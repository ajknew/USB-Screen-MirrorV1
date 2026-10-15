package com.dolanan.usbscreenmirror.model

data class VideoSettings(val resolution: Resolution = Resolution.P480, val fps: Int = 24, val bitrateMbps: Float = 1f, val preset: Preset = Preset.CUSTOM) {
    fun validate(): VideoSettings = copy(fps = fps.coerceIn(15, 30), bitrateMbps = bitrateMbps.coerceIn(.5f, 8f))
    fun bitrateBitsPerSecond(): Int = (validate().bitrateMbps * 1_000_000f).toInt()
    companion object {
        fun forPreset(preset: Preset) = when (preset) {
            Preset.SAVE -> VideoSettings(Resolution.P480, 15, .5f, preset)
            Preset.BALANCED -> VideoSettings(Resolution.P540, 24, 1.5f, preset)
            Preset.HIGH -> VideoSettings(Resolution.P720, 30, 4f, preset)
            Preset.CUSTOM -> VideoSettings()
        }
    }
}
enum class Resolution(val label: String, val height: Int) { P480("480p", 480), P540("540p", 540), P720("720p", 720), AUTO("AUTO", 0) }
enum class Preset(val label: String) { SAVE("HEMAT"), BALANCED("SEIMBANG"), HIGH("KUALITAS TINGGI"), CUSTOM("KUSTOM") }
enum class ScaleMode { FIT, FILL }
data class LayerSettings(val width: Float = 1f, val height: Float = 1f, val x: Float = 0f, val y: Float = 0f, val lockAspect: Boolean = true, val scaleMode: ScaleMode = ScaleMode.FIT, val visible: Boolean = true, val locked: Boolean = false) {
    fun normalized() = copy(width = width.coerceIn(.08f, 1.5f), height = height.coerceIn(.08f, 1.5f), x = x.coerceIn(-.75f, .95f), y = y.coerceIn(-.75f, .95f))
    companion object { val DEFAULT_MIRROR = LayerSettings(); val DEFAULT_CAMERA = LayerSettings(.28f, .28f, .58f, .58f, true, ScaleMode.FILL) }
}
