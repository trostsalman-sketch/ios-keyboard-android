package com.trost.ioskeyboard

data class Palette(
    val bg: Int,
    val key: Int,
    val fnKey: Int,
    val text: Int,
    val accent: Int,
    val accentPressed: Int,
    val shadow: Int
) {
    companion object {
        fun light() = Palette(
            bg = 0xFFE6DCF7.toInt(),
            key = 0xFFFFFFFF.toInt(),
            fnKey = 0xFFC4B2EA.toInt(),
            text = 0xFF22163F.toInt(),
            accent = 0xFF7C3AED.toInt(),
            accentPressed = 0xFF6327CC.toInt(),
            shadow = 0xFF9C88C4.toInt()
        )

        fun dark() = Palette(
            bg = 0xFF1A1229.toInt(),
            key = 0xFF3D2D60.toInt(),
            fnKey = 0xFF2A1F44.toInt(),
            text = 0xFFFFFFFF.toInt(),
            accent = 0xFF8B5CF6.toInt(),
            accentPressed = 0xFF7046D9.toInt(),
            shadow = 0xFF0C0814.toInt()
        )
    }
}