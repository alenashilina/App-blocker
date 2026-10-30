package app.focuslock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText

/**
 * Emergency unlock: type a sentence (no pasting), wait (30 s by default, set per mode), then either
 * unlock one app for a while ("pkg" extra) or turn off a running mode
 * or end a quick block ("modeId" + "action" extras; action is "off_today", "off" or "cancel").
 */
class EmergencyActivity : Activity() {
    private lateinit var store: Store
    private lateinit var ui: Ui
    private val handler = Handler(Looper.getMainLooper())

    private var stage = 0 // 0 = typing, 1 = waiting, 2 = choosing
    private var waitSeconds = EMERGENCY_WAIT_SECONDS
    private var remaining = waitSeconds

    private val pkg: String? get() = intent.getStringExtra("pkg")
    private val modeId: String? get() = intent.getStringExtra("modeId")
    private val action: String get() = intent.getStringExtra("action") ?: "off_today"

    private val tick = object : Runnable {
        override fun run() {
            remaining--
            if (remaining <= 0) stage = 2 else handler.postDelayed(this, 1000)
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        waitSeconds = pkg?.let { store.waitFor(it) }
            ?: modeId?.let { store.get(it)?.emergencyWait }
            ?: EMERGENCY_WAIT_SECONDS
        title = "Emergency unlock"
        ui = Ui(this, screen())
        render()
    }

    // Leaving the screen restarts the wait.
    override fun onResume() {
        super.onResume()
        if (stage == 1) {
            remaining = waitSeconds
            handler.postDelayed(tick, 1000)
            render()
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    private fun target(): String = pkg?.let { appLabel(this, it) }
        ?: modeId?.let { id -> store.get(id)?.let { displayName(this, it) } }
        ?: ""

    private fun render() {
        ui.clear()
        when (stage) {
            0 -> {
                ui.text("To unlock ${target()}, type this sentence:")
                // The sentence stays on screen while you type it.
                ui.text(EMERGENCY_PHRASE, size = 18f, bold = true).apply {
                    background = cardBackground()
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                }
                ui.text("Capital letters don't matter.", size = 13f, dim = true)
                val input = EditText(this).apply {
                    hint = "Type it here"
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    // No copy/paste.
                    isLongClickable = false
                    customSelectionActionModeCallback = NoActions
                    customInsertionActionModeCallback = NoActions
                }
                ui.add(input)
                val go = ui.button("Continue", enabled = false) {
                    if (waitSeconds <= 0) {
                        stage = 2
                    } else {
                        stage = 1
                        remaining = waitSeconds
                        handler.postDelayed(tick, 1000)
                    }
                    render()
                }
                input.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        go.isEnabled = normalize(s.toString()) == normalize(EMERGENCY_PHRASE)
                    }
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                })
            }
            1 -> {
                ui.text("Take a breath. Do you really need ${target()} right now?", size = 18f, center = true)
                ui.text("$remaining", size = 56f, bold = true, center = true)
            }
            2 -> {
                val p = pkg
                if (p != null) {
                    ui.text("Unlock ${target()} for:", size = 18f)
                    for (minutes in listOf(5, 15, 30)) {
                        ui.button("$minutes minutes") {
                            store.unlockApp(p, minutes)
                            packageManager.getLaunchIntentForPackage(p)?.let {
                                startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                            finish()
                        }
                    }
                } else {
                    val label = when (action) {
                        "off_today" -> "Turn off for today"
                        "cancel" -> "End this block now"
                        else -> "Turn off"
                    }
                    ui.button("$label: ${target()}") {
                        store.get(modeId ?: "")?.let { m ->
                            when (action) {
                                "cancel" -> store.delete(m.id)
                                "off_today" -> { m.offDate = today(); store.save(m) }
                                else -> { m.enabled = false; store.save(m) }
                            }
                        }
                        finish()
                    }
                }
            }
        }
        ui.button("Never mind") { leave() }
    }

    @Deprecated("Still called on this app's target SDK")
    override fun onBackPressed() = leave()

    /** Backing out of unlocking an app goes home, not into the blocked app. */
    private fun leave() = if (pkg != null) goHome() else finish()

    /** Ignores capital letters, extra spaces and a final full stop. */
    private fun normalize(s: String) =
        s.lowercase().trim().trimEnd('.', '!').replace(Regex("\\s+"), " ")

    private object NoActions : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = false
        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false
        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false
        override fun onDestroyActionMode(mode: ActionMode?) {}
    }
}
