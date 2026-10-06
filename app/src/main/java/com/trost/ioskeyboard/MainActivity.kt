package com.trost.ioskeyboard

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val purple = 0xFF7C3AED.toInt()
    private val textDark = 0xFF22163F.toInt()
    private val textGray = 0xFF6B5E86.toInt()
    private val okGreen = 0xFF16A34A.toInt()
    private val pageBg = 0xFFF3EEFC.toInt()

    private lateinit var prefs: SharedPreferences
    private lateinit var s1: TextView
    private lateinit var s2: TextView
    private lateinit var b1: TextView
    private lateinit var b2: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(pageBg)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(16), dp(16), dp(32))
        col.isFocusable = true
        col.isFocusableInTouchMode = true
        scroll.addView(col)

        // Header
        val header = LinearLayout(this)
        header.orientation = LinearLayout.VERTICAL
        header.setPadding(dp(22), dp(26), dp(22), dp(26))
        header.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF6D28D9.toInt(), 0xFFA855F7.toInt())
        ).apply { cornerRadius = dp(22).toFloat() }
        header.addView(text("iOS Клавиатура", 26f, Color.WHITE, true))
        header.addView(
            text("Клавиатура в стиле iPhone для Android. Три шага, и готово.", 15f, 0xE6FFFFFF.toInt(), false)
                .apply { setPadding(0, dp(8), 0, 0) }
        )
        col.addView(header, lp(0))

        // Step 1
        val c1 = card()
        c1.addView(text("Шаг 1. Включите клавиатуру", 18f, textDark, true))
        c1.addView(
            text(
                "Откройте список клавиатур и включите «iOS Клавиатура». Android покажет стандартное предупреждение для сторонних клавиатур, просто нажмите «ОК».",
                14f, textGray, false
            ).apply { setPadding(0, dp(6), 0, dp(12)) }
        )
        b1 = button("Открыть настройки") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        c1.addView(b1)
        s1 = text("", 14f, textGray, true).apply { setPadding(0, dp(10), 0, 0) }
        c1.addView(s1)
        col.addView(c1, lp(14))

        // Step 2
        val c2 = card()
        c2.addView(text("Шаг 2. Выберите её", 18f, textDark, true))
        c2.addView(
            text("Нажмите кнопку и выберите «iOS Клавиатура» в списке.", 14f, textGray, false)
                .apply { setPadding(0, dp(6), 0, dp(12)) }
        )
        b2 = button("Выбрать клавиатуру") {
            if (!isEnabledIme()) {
                Toast.makeText(this, "Сначала выполните шаг 1", Toast.LENGTH_SHORT).show()
            } else {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }
        c2.addView(b2)
        s2 = text("", 14f, textGray, true).apply { setPadding(0, dp(10), 0, 0) }
        c2.addView(s2)
        col.addView(c2, lp(14))

        // Step 3
        val c3 = card()
        c3.addView(text("Шаг 3. Попробуйте", 18f, textDark, true))
        val et = EditText(this)
        et.hint = "Напишите что-нибудь…"
        et.textSize = 16f
        et.setTextColor(textDark)
        et.setHintTextColor(0xFF9C8FB8.toInt())
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        et.minLines = 2
        et.gravity = Gravity.TOP
        et.setPadding(dp(14), dp(12), dp(14), dp(12))
        et.background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(pageBg) }
        c3.addView(et, lp(10))
        col.addView(c3, lp(14))

        // Settings
        val c4 = card()
        c4.addView(text("Настройки", 18f, textDark, true).apply { setPadding(0, 0, 0, dp(4)) })
        switchRow(c4, "Звук клавиш", Prefs.SOUND, true)
        switchRow(c4, "Вибрация", Prefs.VIBRATE, true)
        switchRow(c4, "Заглавная в начале предложения", Prefs.AUTOCAP, true)
        switchRow(c4, "Точка двойным пробелом", Prefs.DOUBLE_SPACE, true)
        switchRow(c4, "Тёмная тема", Prefs.DARK, false)
        col.addView(c4, lp(14))

        // Tips
        val c5 = card()
        c5.addView(text("Подсказки", 18f, textDark, true))
        c5.addView(
            text(
                "• 🌐 меняет язык, удержание 🌐 открывает другие клавиатуры\n" +
                    "• Двойное нажатие ⇧ включает Caps Lock\n" +
                    "• Удержание «е» даёт «ё», удержание «ь» даёт «ъ»\n" +
                    "• Два пробела подряд ставят точку\n" +
                    "• Удержание ⌫ стирает быстро",
                14f, textGray, false
            ).apply { setPadding(0, dp(6), 0, 0) }
        )
        col.addView(c5, lp(14))

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refresh()
    }

    private fun refresh() {
        if (!::s1.isInitialized) return
        val en = isEnabledIme()
        val sel = isSelectedIme()
        s1.text = if (en) "✓ Включена" else "○ Пока не включена"
        s1.setTextColor(if (en) okGreen else textGray)
        s2.text = if (sel) "✓ Выбрана. Можно печатать!" else "○ Пока не выбрана"
        s2.setTextColor(if (sel) okGreen else textGray)
        b1.alpha = if (en) 0.55f else 1f
        b2.alpha = if (en && !sel) 1f else 0.55f
    }

    private fun isEnabledIme(): Boolean {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == packageName }
    }

    private fun isSelectedIme(): Boolean {
        val id = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return false
        return id.startsWith("$packageName/")
    }

    // ---------- UI helpers ----------

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun lp(top: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(top) }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(18), dp(16), dp(18), dp(16))
        c.background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.WHITE) }
        c.elevation = dp(2).toFloat()
        return c
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean): TextView {
        val tv = TextView(this)
        tv.text = s
        tv.textSize = size
        tv.setTextColor(color)
        if (bold) tv.typeface = Typeface.DEFAULT_BOLD
        tv.setLineSpacing(0f, 1.15f)
        return tv
    }

    private fun button(label: String, onClick: () -> Unit): TextView {
        val b = TextView(this)
        b.text = label
        b.textSize = 16f
        b.setTextColor(Color.WHITE)
        b.typeface = Typeface.DEFAULT_BOLD
        b.gravity = Gravity.CENTER
        b.setPadding(dp(16), dp(14), dp(16), dp(14))
        b.background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(purple) }
        b.setOnClickListener { onClick() }
        return b
    }

    private fun switchRow(parent: LinearLayout, title: String, key: String, def: Boolean) {
        val sw = Switch(this)
        sw.text = title
        sw.textSize = 16f
        sw.setTextColor(textDark)
        sw.isChecked = prefs.getBoolean(key, def)
        sw.setPadding(0, dp(10), 0, dp(10))
        sw.setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply() }
        parent.addView(
            sw,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
    }
}