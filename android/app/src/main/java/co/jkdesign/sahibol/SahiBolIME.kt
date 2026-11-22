package co.jkdesign.sahibol

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.concurrent.thread

/** Sahi Bol keyboard: normal typing + a "Fix" bar that corrects the whole message. */
class SahiBolIME : InputMethodService() {

    private val green = Color.parseColor("#0B7A53")
    private val ink = Color.parseColor("#13201A")
    private val muted = Color.parseColor("#4F5D56")
    private val keyGrey = Color.parseColor("#B9C2BD")
    private val main = Handler(Looper.getMainLooper())

    private lateinit var status: TextView
    private lateinit var resultBox: LinearLayout
    private lateinit var resultText: TextView
    private lateinit var reasonsText: TextView

    private var caps = false
    private val letterKeys = mutableListOf<Button>()
    private var pendingFixed: String? = null
    private var busy = false

    override fun onCreateInputView(): View {
        letterKeys.clear()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#D8DEDB"))
            setPadding(dp(4), dp(6), dp(4), dp(8))
        }

        // --- Result card (hidden until a fix comes back) ---
        resultBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 14)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            visibility = View.GONE
        }
        resultText = TextView(this).apply { setTextColor(ink); textSize = 16f; setTypeface(typeface, Typeface.BOLD) }
        reasonsText = TextView(this).apply { setTextColor(muted); textSize = 13f; setPadding(0, dp(6), 0, dp(8)) }
        val resultButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        resultButtons.addView(pill("Keep mine", false) { hideResult() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        resultButtons.addView(pill("Use this", true) { applyFix() }, LinearLayout.LayoutParams(0, dp(44), 2f))
        resultBox.addView(resultText)
        resultBox.addView(reasonsText)
        resultBox.addView(resultButtons)
        root.addView(resultBox, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })

        // --- Fix bar ---
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bar.addView(pill("✓ Fix", true) { fix("normal") }, chipParams())
        bar.addView(pill("Polite", false) { fix("polite") }, chipParams())
        bar.addView(pill("Short", false) { fix("short") }, chipParams())
        bar.addView(pill("हिंदी", false) { fix("hindi") }, chipParams())
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(bar) })

        status = TextView(this).apply { setTextColor(muted); textSize = 12f; setPadding(dp(6), dp(4), dp(6), dp(4)); visibility = View.GONE }
        root.addView(status)

        // --- Keys ---
        root.addView(row("1234567890".map { c -> key(c.toString()) { type(c.toString()) } }))
        root.addView(row("qwertyuiop".map { letter(it) }))
        root.addView(row("asdfghjkl".map { letter(it) }, sidePad = 16))
        root.addView(row(
            listOf(key("⇧", 1.4f, keyGrey) { toggleCaps() }) +
                "zxcvbnm".map { letter(it) } +
                listOf(key("⌫", 1.4f, keyGrey) { backspace() })
        ))
        root.addView(row(listOf(
            key("🌐", 1.2f, keyGrey) { switchToNextInputMethod(false) },
            key(",") { type(",") },
            key("space", 4.5f) { type(" ") },
            key(".") { type(".") },
            key("↵", 1.6f, keyGrey) { sendKeyChar('\n') },
        )))
        return root
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (::resultBox.isInitialized) hideResult()
    }

    // ---------- Fixing ----------
    private fun fix(tone: String) {
        if (busy) return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(2000, 0)?.toString() ?: ""
        val after = ic.getTextAfterCursor(2000, 0)?.toString() ?: ""
        val text = before + after
        if (text.isBlank()) { showStatus("Type a message first"); return }

        busy = true
        showStatus("Fixing…")
        thread {
            try {
                val r = Api.fix(text, tone)
                main.post {
                    busy = false
                    status.visibility = View.GONE
                    pendingFixed = r.fixed
                    resultText.text = r.fixed
                    reasonsText.text = if (r.reasons.isEmpty()) "Already good 👍"
                        else r.reasons.joinToString("\n") { "• $it" }
                    resultBox.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                main.post { busy = false; showStatus("No internet or server busy. Try again.") }
            }
        }
    }

    private fun applyFix() {
        val fixed = pendingFixed ?: return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(2000, 0)?.length ?: 0
        val after = ic.getTextAfterCursor(2000, 0)?.length ?: 0
        ic.beginBatchEdit()
        ic.deleteSurroundingText(before, after)
        ic.commitText(fixed, 1)
        ic.endBatchEdit()
        hideResult()
    }

    private fun hideResult() {
        pendingFixed = null
        resultBox.visibility = View.GONE
    }

    private fun showStatus(msg: String) {
        status.text = msg
        status.visibility = View.VISIBLE
    }

    // ---------- Typing ----------
    private fun type(s: String) {
        currentInputConnection?.commitText(s, 1)
        if (caps) toggleCaps()
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) ic.commitText("", 1) else ic.deleteSurroundingText(1, 0)
    }

    private fun toggleCaps() {
        caps = !caps
        letterKeys.forEach { b -> b.text = if (caps) b.text.toString().uppercase() else b.text.toString().lowercase() }
    }

    // ---------- Views ----------
    private fun letter(c: Char): Button {
        val b = key(c.toString()) {}
        b.setOnClickListener { type(b.text.toString()) }
        letterKeys += b
        return b
    }

    private fun key(label: String, weight: Float = 1f, bg: Int = Color.WHITE, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = if (label.length > 1 && label != "space") 16f else 18f
            setTextColor(ink)
            background = rounded(bg, 8)
            stateListAnimator = null
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, dp(46), weight).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) }
            setOnClickListener { onClick() }
        }

    private fun pill(label: String, primary: Boolean, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            setTypeface(typeface, if (primary) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(if (primary) Color.WHITE else ink)
            background = rounded(if (primary) green else Color.WHITE, 22)
            stateListAnimator = null
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(dp(16), 0, dp(16), 0)
            setOnClickListener { onClick() }
        }

    private fun chipParams() = LinearLayout.LayoutParams(-2, dp(44)).apply { setMargins(dp(2), 0, dp(4), dp(2)) }

    private fun row(keys: List<View>, sidePad: Int = 0) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(sidePad), 0, dp(sidePad), 0)
        keys.forEach { addView(it) }
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
