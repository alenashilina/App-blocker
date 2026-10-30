package app.focuslock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout

/** The screen shown instead of a blocked app. */
class BlockActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()

        val pkg = intent.getStringExtra("pkg") ?: ""
        val mode = Store(this).get(intent.getStringExtra("modeId") ?: "")

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        setContentView(box)
        val ui = Ui(this, box)

        ui.text(if (mode == null || mode.quick) "⏳" else mode.emoji, size = 56f, center = true)
        ui.text("${appLabel(this, pkg)} is blocked", size = 26f, bold = true, center = true)
        if (mode != null) {
            val source = if (mode.quick) "Quick block" else displayName(this, mode)
            ui.text("$source · until ${mode.untilText()}", size = 16f, dim = true, center = true)
        }
        ui.text("", size = 8f)
        ui.button("OK") { goHome() }
        box.addView(Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = "Emergency unlock"
            isAllCaps = false
            alpha = 0.7f
            setOnClickListener {
                startActivity(Intent(this@BlockActivity, EmergencyActivity::class.java).putExtra("pkg", pkg))
                finish()
            }
        })
    }

    // Back goes home, not back into the blocked app.
    @Deprecated("Still called on this app's target SDK")
    override fun onBackPressed() = goHome()
}
