package com.trost.ioskeyboard

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val SOUND = "sound"
    const val VIBRATE = "vibrate"
    const val AUTOCAP = "autocap"
    const val DOUBLE_SPACE = "double_space"
    const val DARK = "dark"
    const val LANG = "lang"

    fun get(c: Context): SharedPreferences =
        c.getSharedPreferences("settings", Context.MODE_PRIVATE)
}