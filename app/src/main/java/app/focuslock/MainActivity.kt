package app.focuslock

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/** Home screen: your modes (each with an on/off switch) and quick blocks. */
class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var ui: Ui

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        ui = Ui(this, screen())
        if (store.firstLaunch()) startActivity(Intent(this, HelpActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        ui.clear()

        if (!serviceEnabled()) {
            ui.text("⚠️ Blocker is off", size = 17f, bold = true)
            ui.text(
                "Tap the button, open Installed apps → Focus Lock, and turn it on.\n\n" +
                    "If the switch is greyed out: Settings → Apps → Focus Lock → ⋮ (top right) → " +
                    "Allow restricted settings, then try again.",
                dim = true
            )
            ui.button("Turn on blocker") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        val all = store.modes()

        ui.header("Modes")
        val modes = all.filter { !it.quick }
        if (modes.isEmpty()) ui.text("No modes yet. Make one for work, mornings, or anything else.", dim = true)
        modes.forEach { card(it) }
        ui.button("+  New mode") { edit(null, quick = false) }

        ui.header("Quick blocks")
        val quick = all.filter { it.quick }
        if (quick.isEmpty()) ui.text("Like a timer: block apps from now until a time you pick. It disappears when it ends.", dim = true)
        quick.forEach { card(it) }
        ui.button("+  Block apps now") { edit(null, quick = true) }

        ui.text("", size = 8f)
        ui.button("📖  How to use · Как пользоваться") {
            startActivity(Intent(this, HelpActivity::class.java))
        }
    }

    private fun card(m: Mode) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBackground()
            setPadding(dp(14), dp(12), dp(8), dp(12))
            setOnClickListener { if (m.quick) endQuickBlock(m) else edit(m.id, false) }
        }

        // Icon: the emoji for a mode, the first app's icon for a quick block.
        val icon = if (m.quick) m.apps.firstOrNull()?.let { appIcon(this, it) } else null
        if (icon != null) {
            row.addView(ImageView(this).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(dp(40), dp(40)))
        } else {
            row.addView(TextView(this).apply {
                text = if (m.quick) "⏳" else m.emoji
                textSize = 28f
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        val middle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        middle.addView(TextView(this).apply {
            text = if (m.quick) appsText(this@MainActivity, m.apps) else displayName(this@MainActivity, m)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
        })
        val details = if (m.quick) m.scheduleText() else "${m.scheduleText()}\n${appsText(this, m.apps)}"
        middle.addView(TextView(this).apply {
            text = details
            textSize = 13f
            alpha = 0.7f
        })
        val status = when {
            m.quick -> null
            m.isActive() -> "● Blocking until ${m.untilText()}"
            m.enabled && !m.isOn -> "Off for today"
            m.apps.isEmpty() -> "No apps chosen"
            else -> null
        }
        if (status != null) {
            middle.addView(TextView(this).apply {
                text = status
                textSize = 13f
                if (m.isActive()) setTextColor(Color.parseColor("#2E9E5B"))
            })
        }
        row.addView(middle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        if (!m.quick) {
            row.addView(Switch(this).apply {
                isChecked = m.isOn
                setOnCheckedChangeListener { _, on -> onToggle(m, on) }
            })
        }

        ui.add(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })
    }

    private fun onToggle(m: Mode, on: Boolean) {
        if (on) {
            m.enabled = true
            m.offDate = ""
            store.save(m)
            ui.root.post { render() }
            return
        }
        // Put the switch back until the choice is confirmed.
        ui.root.post { render() }
        AlertDialog.Builder(this)
            .setTitle("Turn off ${displayName(this, m)}?")
            .setItems(arrayOf("Just for today", "Until I turn it back on")) { _, i ->
                val action = if (i == 0) "off_today" else "off"
                if (m.isActive()) {
                    // It's blocking right now, so this needs an emergency unlock.
                    startActivity(
                        Intent(this, EmergencyActivity::class.java)
                            .putExtra("modeId", m.id)
                            .putExtra("action", action)
                    )
                } else {
                    if (i == 0) m.offDate = today() else m.enabled = false
                    store.save(m)
                    render()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun endQuickBlock(m: Mode) {
        AlertDialog.Builder(this)
            .setTitle("Ends at ${m.untilText()}")
            .setMessage("${m.scheduleText()}. Ending it early needs an emergency unlock.")
            .setPositiveButton("End early") { _, _ ->
                startActivity(
                    Intent(this, EmergencyActivity::class.java)
                        .putExtra("modeId", m.id)
                        .putExtra("action", "cancel")
                )
            }
            .setNegativeButton("Keep it", null)
            .show()
    }

    private fun edit(id: String?, quick: Boolean) {
        startActivity(Intent(this, EditModeActivity::class.java).putExtra("id", id).putExtra("quick", quick))
    }

    private fun serviceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(this, BlockerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
