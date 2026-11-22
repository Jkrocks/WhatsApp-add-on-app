package co.jkdesign.sahibol

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Setup screen: turn on the keyboard, then try it. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
            setBackgroundColor(Color.WHITE)
        }
        fun text(s: String, size: Float, bold: Boolean = false) = TextView(this).apply {
            text = s; textSize = size; setTextColor(Color.parseColor("#13201A"))
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(6), 0, dp(6))
        }
        fun button(s: String, onClick: () -> Unit) = Button(this).apply {
            text = s; isAllCaps = false; setOnClickListener { onClick() }
        }

        col.addView(text("Sahi Bol", 28f, true))
        col.addView(text("Type like you talk. Send like a pro.", 16f))
        col.addView(text("\nStep 1: Turn on the keyboard", 18f, true))
        col.addView(button("Open keyboard settings") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        col.addView(text("Step 2: Choose Sahi Bol", 18f, true))
        col.addView(button("Choose keyboard") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        col.addView(text("Step 3: Try it", 18f, true))
        col.addView(EditText(this).apply {
            hint = "price is less, you take 2 i give discount"
            minLines = 3
        })
        col.addView(text("Type a message, then tap ✓ Fix. In WhatsApp, it works the same way.", 14f))

        setContentView(ScrollView(this).apply { addView(col) })
    }
}
