package co.jkdesign.sahibol

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
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

    // Keycap palette: face colour + darker "lip" under it
    private data class Cap(val face: Int, val lip: Int, val text: Int)
    private fun c(h: String) = Color.parseColor(h)
    private val letterCap = Cap(c("#3A3D40"), c("#1F2123"), c("#F2F3F4"))
    private val numberCap = Cap(c("#C9CDD1"), c("#8A9096"), c("#141414"))
    private val darkCap = Cap(c("#26282A"), c("#121314"), c("#F2F3F4"))
    private val orangeCap = Cap(c("#FF6A13"), c("#B04606"), c("#111111"))
    private val pinkCap = Cap(c("#FF45D2"), c("#A8238A"), c("#111111"))
    private val blueCap = Cap(c("#9BBCCB"), c("#5E7F8F"), c("#111111"))
    private val whiteCap = Cap(c("#EEF0F2"), c("#A3A9AF"), c("#111111"))
    private val jkCap = Cap(c("#FFD21F"), c("#B8930A"), c("#111111"))
    private val navyCap = Cap(c("#1F3C91"), c("#10225A"), c("#FFFFFF"))
    private val ink = c("#141414")
    private val muted = c("#55595D")
    private val bold by lazy { Typeface.create("sans-serif-condensed", Typeface.BOLD) }
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
            setBackgroundColor(c("#0E0F10"))
            setPadding(dp(4), dp(6), dp(4), dp(8))
        }

        // --- Result card (hidden until a fix comes back) ---
        resultBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(c("#EEF0F2"), 14)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            visibility = View.GONE
        }
        resultText = TextView(this).apply { setTextColor(ink); textSize = 16f; typeface = bold }
        reasonsText = TextView(this).apply { setTextColor(muted); textSize = 13f; setPadding(0, dp(6), 0, dp(8)) }
        val resultButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        resultButtons.addView(pill("KEEP MINE", whiteCap) { hideResult() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        resultButtons.addView(pill("USE THIS", orangeCap) { applyFix() }, LinearLayout.LayoutParams(0, dp(48), 2f))
        resultBox.addView(resultText)
        resultBox.addView(reasonsText)
        resultBox.addView(resultButtons)
        root.addView(resultBox, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })

        // --- Fix bar ---
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bar.addView(pill("✓ FIX", orangeCap) { fix("normal") }, chipParams())
        bar.addView(pill("POLITE", pinkCap) { fix("polite") }, chipParams())
        bar.addView(pill("SHORT", blueCap) { fix("short") }, chipParams())
        bar.addView(pill("हिंदी", whiteCap) { fix("hindi") }, chipParams())
        bar.addView(pill("JK", jkCap) {}.apply { isClickable = false; contentDescription = "JK Design" }, chipParams())
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(bar) })

        status = TextView(this).apply { setTextColor(c("#B8BCC0")); textSize = 12f; setPadding(dp(6), dp(4), dp(6), dp(4)); visibility = View.GONE }
        root.addView(status)

        // --- Keys ---
        root.addView(row("1234567890".map { c -> key(c.toString(), cap = numberCap) { type(c.toString()) } }))
        root.addView(row("qwertyuiop".map { letter(it) }))
        root.addView(row("asdfghjkl".map { letter(it) }, sidePad = 16))
        root.addView(row(
            listOf(key("⇧", 1.4f, darkCap) { toggleCaps() }) +
                "zxcvbnm".map { letter(it) } +
                listOf(key("⌫", 1.4f, darkCap) { backspace() })
        ))
        root.addView(row(listOf(
            key("⌨", 1.2f, darkCap) { switchToNextInputMethod(false) },
            key(",", cap = darkCap) { type(",") },
            key("SAHI BOL", 4.5f, navyCap) { type(" ") },
            key(".", cap = darkCap) { type(".") },
            key("↵", 1.6f, orangeCap) { sendKeyChar('\n') },
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

    private fun key(label: String, weight: Float = 1f, cap: Cap = letterCap, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            typeface = bold
            textSize = if (label == "SAHI BOL") 12f else 19f
            letterSpacing = if (label == "SAHI BOL") 0.12f else 0f
            setTextColor(cap.text)
            background = keycap(cap, 7)
            stateListAnimator = null
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(0, 0, 0, dp(4))
            layoutParams = LinearLayout.LayoutParams(0, dp(48), weight).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) }
            setOnClickListener { onClick() }
        }

    private fun pill(label: String, cap: Cap, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            typeface = bold
            textSize = 14f
            letterSpacing = 0.06f
            setTextColor(cap.text)
            background = keycap(cap, 10)
            stateListAnimator = null
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(dp(16), 0, dp(16), dp(4))
            setOnClickListener { onClick() }
        }

    /** Chunky keycap: darker lip underneath, face on top; face sinks when pressed. */
    private fun keycap(cap: Cap, radiusDp: Int): StateListDrawable {
        fun layers(topInset: Int, bottomInset: Int) = LayerDrawable(arrayOf(rounded(cap.lip, radiusDp), rounded(cap.face, radiusDp))).apply {
            setLayerInset(1, 0, dp(topInset), 0, dp(bottomInset))
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), layers(3, 1))
            addState(intArrayOf(), layers(0, 4))
        }
    }

    private fun chipParams() = LinearLayout.LayoutParams(-2, dp(48)).apply { setMargins(dp(2), 0, dp(4), dp(2)) }

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
