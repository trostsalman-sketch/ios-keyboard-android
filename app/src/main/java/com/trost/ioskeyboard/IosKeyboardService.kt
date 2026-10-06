package com.trost.ioskeyboard

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView

class IosKeyboardService : InputMethodService() {

    private enum class Mode { LETTERS, NUMBERS, SYMBOLS }
    private enum class Shift { OFF, ON, LOCK }
    private enum class T { CHAR, SHIFT, BACK, TO_NUM, TO_SYM, TO_ABC, GLOBE, SPACE, ENTER, SPACER }

    private class Key(val label: String, val type: T, val weight: Float = 1f)

    private lateinit var prefs: SharedPreferences
    private var root: LinearLayout? = null
    private var mode = Mode.LETTERS
    private var shift = Shift.OFF
    private var lang = "ru"
    private var lastShiftTap = 0L
    private var pal = Palette.light()
    private val handler = Handler(Looper.getMainLooper())

    private val alts = mapOf("е" to "ё", "ь" to "ъ")

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        lang = prefs.getString(Prefs.LANG, "ru") ?: "ru"
    }

    override fun onCreateInputView(): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.VERTICAL
        root = r
        render()
        return r
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val cls = (info?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
        mode = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) Mode.NUMBERS else Mode.LETTERS
        shift = Shift.OFF
        updateAutoCap(false)
        render()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        handler.removeCallbacksAndMessages(null)
        super.onFinishInputView(finishingInput)
    }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private fun dp(v: Int): Int = dp(v.toFloat())

    // ---------- layout ----------

    private fun keys(s: String, w: Float = 1f): List<Key> = s.map { Key(it.toString(), T.CHAR, w) }

    private fun spacer(w: Float) = Key("", T.SPACER, w)

    private fun rows(): List<List<Key>> {
        val ru = lang == "ru"
        val modeKey = if (mode == Mode.LETTERS) Key("123", T.TO_NUM, 1.25f)
        else Key(if (ru) "АБВ" else "ABC", T.TO_ABC, 1.25f)
        val bottom = listOf(
            modeKey,
            Key("🌐", T.GLOBE, 1.25f),
            Key(if (ru) "пробел" else "space", T.SPACE, 5f),
            Key(enterLabel(), T.ENTER, 2.5f)
        )
        val back = Key("⌫", T.BACK, 1.5f)
        return when (mode) {
            Mode.LETTERS -> if (ru) listOf(
                keys("йцукенгшщзх"),
                keys("фывапролджэ"),
                listOf(Key("⇧", T.SHIFT, 1.25f)) + keys("ячсмитьбю") + listOf(Key("⌫", T.BACK, 1.25f)),
                bottom
            ) else listOf(
                keys("qwertyuiop"),
                listOf(spacer(0.5f)) + keys("asdfghjkl") + listOf(spacer(0.5f)),
                listOf(Key("⇧", T.SHIFT, 1.5f), spacer(0.25f)) + keys("zxcvbnm") + listOf(spacer(0.25f), back),
                bottom
            )
            Mode.NUMBERS -> listOf(
                keys("1234567890"),
                keys("-/:;()₽&@\""),
                listOf(Key("#+=", T.TO_SYM, 1.5f), spacer(0.25f)) + keys(".,?!'", 1.3f) + listOf(spacer(0.25f), back),
                bottom
            )
            Mode.SYMBOLS -> listOf(
                keys("[]{}#%^*+="),
                keys("_\\|~<>€\$£•"),
                listOf(Key("123", T.TO_NUM, 1.5f), spacer(0.25f)) + keys(".,?!'", 1.3f) + listOf(spacer(0.25f), back),
                bottom
            )
        }
    }

    private fun render() {
        val r = root ?: return
        handler.removeCallbacksAndMessages(null)
        pal = if (prefs.getBoolean(Prefs.DARK, false)) Palette.dark() else Palette.light()
        r.removeAllViews()
        r.setBackgroundColor(pal.bg)
        r.setPadding(dp(3), dp(6), dp(3), dp(6))
        for (row in rows()) r.addView(buildRow(row))
    }

    private fun buildRow(keys: List<Key>): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54))
        for (k in keys) {
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, k.weight)
            if (k.type == T.SPACER) {
                row.addView(View(this), lp)
                continue
            }
            lp.setMargins(dp(3), dp(5), dp(3), dp(5))
            row.addView(buildKey(k), lp)
        }
        return row
    }

    private fun keyBg(color: Int): Drawable {
        val radius = dp(6).toFloat()
        val shadow = GradientDrawable().apply { cornerRadius = radius; setColor(pal.shadow) }
        val face = GradientDrawable().apply { cornerRadius = radius; setColor(color) }
        val ld = LayerDrawable(arrayOf<Drawable>(shadow, face))
        ld.setLayerInset(1, 0, 0, 0, dp(1))
        return ld
    }

    private fun buildKey(k: Key): View {
        val tv = TextView(this)
        tv.gravity = Gravity.CENTER
        tv.includeFontPadding = false
        tv.maxLines = 1

        var label = k.label
        if (k.type == T.CHAR && mode == Mode.LETTERS && shift != Shift.OFF) label = label.uppercase()
        if (k.type == T.SHIFT) label = if (shift == Shift.LOCK) "⇪" else "⇧"
        tv.text = label

        val size = when {
            k.type == T.CHAR && mode == Mode.LETTERS -> 22f
            k.type == T.CHAR -> 20f
            k.type == T.SHIFT || k.type == T.BACK -> 21f
            k.type == T.GLOBE -> 18f
            else -> 15f
        }
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)

        val isFn = k.type != T.CHAR && k.type != T.SPACE
        val shiftActive = k.type == T.SHIFT && shift != Shift.OFF
        val actionEnter = k.type == T.ENTER && hasAction()
        val normal = when {
            actionEnter -> pal.accent
            shiftActive -> pal.key
            isFn -> pal.fnKey
            else -> pal.key
        }
        val pressed = when {
            actionEnter -> pal.accentPressed
            isFn -> pal.key
            else -> pal.fnKey
        }
        tv.setTextColor(
            when {
                actionEnter -> Color.WHITE
                shiftActive -> pal.accent
                else -> pal.text
            }
        )
        tv.background = keyBg(normal)
        tv.setOnTouchListener(KeyTouch(k, tv, normal, pressed))
        return tv
    }

    // ---------- touch ----------

    private inner class KeyTouch(
        val k: Key,
        val v: TextView,
        val normal: Int,
        val pressed: Int
    ) : View.OnTouchListener {
        private var longFired = false
        private val repeat = object : Runnable {
            override fun run() {
                deleteOne()
                handler.postDelayed(this, 60)
            }
        }
        private val longPress = Runnable { onLong() }

        override fun onTouch(view: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.background = keyBg(pressed)
                    feedback(k.type)
                    longFired = false
                    if (k.type == T.BACK) {
                        deleteOne()
                        handler.postDelayed(repeat, 400)
                    } else {
                        handler.postDelayed(longPress, 450)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(repeat)
                    handler.removeCallbacks(longPress)
                    v.background = keyBg(normal)
                    if (k.type == T.BACK) updateAutoCap(true)
                    else if (!longFired) onKey(k)
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(repeat)
                    handler.removeCallbacks(longPress)
                    v.background = keyBg(normal)
                }
            }
            return true
        }

        private fun onLong() {
            if (k.type == T.CHAR && mode == Mode.LETTERS) {
                val alt = alts[k.label.lowercase()] ?: return
                longFired = true
                feedback(T.CHAR)
                commitChar(alt)
            } else if (k.type == T.GLOBE) {
                longFired = true
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }
    }

    private fun onKey(k: Key) {
        when (k.type) {
            T.CHAR -> commitChar(k.label)
            T.SHIFT -> {
                val now = System.currentTimeMillis()
                shift = when {
                    shift == Shift.LOCK -> Shift.OFF
                    now - lastShiftTap < 350 -> Shift.LOCK
                    shift == Shift.ON -> Shift.OFF
                    else -> Shift.ON
                }
                lastShiftTap = now
                render()
            }
            T.TO_NUM -> { mode = Mode.NUMBERS; render() }
            T.TO_SYM -> { mode = Mode.SYMBOLS; render() }
            T.TO_ABC -> { mode = Mode.LETTERS; updateAutoCap(false); render() }
            T.GLOBE -> {
                lang = if (lang == "ru") "en" else "ru"
                prefs.edit().putString(Prefs.LANG, lang).apply()
                mode = Mode.LETTERS
                updateAutoCap(false)
                render()
            }
            T.SPACE -> space()
            T.ENTER -> enter()
            else -> {}
        }
    }

    // ---------- input ----------

    private fun commitChar(s: String) {
        val ic = currentInputConnection ?: return
        val out = if (mode == Mode.LETTERS && shift != Shift.OFF) s.uppercase() else s
        ic.commitText(out, 1)
        if (mode != Mode.LETTERS && s == "'") {
            mode = Mode.LETTERS
            updateAutoCap(false)
            render()
            return
        }
        if (shift == Shift.ON) {
            shift = Shift.OFF
            render()
        }
    }

    private fun space() {
        val ic = currentInputConnection ?: return
        if (prefs.getBoolean(Prefs.DOUBLE_SPACE, true)) {
            val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                updateAutoCap(true)
                return
            }
        }
        ic.commitText(" ", 1)
        if (mode != Mode.LETTERS) {
            mode = Mode.LETTERS
            updateAutoCap(false)
            render()
            return
        }
        updateAutoCap(true)
    }

    private fun deleteOne() {
        val ic = currentInputConnection ?: return
        val sel = ic.getSelectedText(0)
        if (!sel.isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        val before = ic.getTextBeforeCursor(2, 0)
        when {
            before.isNullOrEmpty() -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            before.length == 2 && Character.isSurrogatePair(before[0], before[1]) -> ic.deleteSurroundingText(2, 0)
            else -> ic.deleteSurroundingText(1, 0)
        }
    }

    private fun hasAction(): Boolean {
        val info = currentInputEditorInfo ?: return false
        if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return false
        val a = info.imeOptions and EditorInfo.IME_MASK_ACTION
        return a == EditorInfo.IME_ACTION_GO || a == EditorInfo.IME_ACTION_SEARCH ||
            a == EditorInfo.IME_ACTION_SEND || a == EditorInfo.IME_ACTION_NEXT ||
            a == EditorInfo.IME_ACTION_DONE
    }

    private fun enterLabel(): String {
        val ru = lang == "ru"
        if (!hasAction()) return if (ru) "ввод" else "return"
        val a = (currentInputEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        return when (a) {
            EditorInfo.IME_ACTION_GO -> if (ru) "Перейти" else "Go"
            EditorInfo.IME_ACTION_SEARCH -> if (ru) "Найти" else "Search"
            EditorInfo.IME_ACTION_SEND -> if (ru) "Отпр." else "Send"
            EditorInfo.IME_ACTION_NEXT -> if (ru) "Далее" else "Next"
            else -> if (ru) "Готово" else "Done"
        }
    }

    private fun enter() {
        val ic = currentInputConnection ?: return
        if (hasAction()) {
            val a = (currentInputEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
            ic.performEditorAction(a)
        } else {
            ic.commitText("\n", 1)
            updateAutoCap(true)
        }
    }

    private fun updateAutoCap(doRender: Boolean) {
        if (shift == Shift.LOCK || mode != Mode.LETTERS) return
        val want = prefs.getBoolean(Prefs.AUTOCAP, true) && shouldCap()
        val ns = if (want) Shift.ON else Shift.OFF
        if (ns != shift) {
            shift = ns
            if (doRender) render()
        }
    }

    private fun shouldCap(): Boolean {
        val info = currentInputEditorInfo ?: return false
        if ((info.inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val noCap = listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI
        )
        if (variation in noCap) return false
        val before = currentInputConnection?.getTextBeforeCursor(3, 0)?.toString() ?: return true
        if (before.isEmpty()) return true
        val last = before.last()
        if (last == '\n') return true
        if (last == ' ') {
            val t = before.trimEnd()
            return t.isNotEmpty() && t.last() in ".!?"
        }
        return false
    }

    // ---------- feedback ----------

    private fun feedback(t: T) {
        if (prefs.getBoolean(Prefs.SOUND, true)) {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val fx = when (t) {
                T.BACK -> AudioManager.FX_KEYPRESS_DELETE
                T.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                T.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            am.playSoundEffect(fx, -1f)
        }
        if (prefs.getBoolean(Prefs.VIBRATE, true)) {
            val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= 26) {
                vib.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vib.vibrate(12)
            }
        }
    }
}