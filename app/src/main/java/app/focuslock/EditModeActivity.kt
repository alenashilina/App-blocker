package app.focuslock

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.icu.text.BreakIterator
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Create or edit a mode (extra "quick" = false) or a quick block ("quick" = true). */
class EditModeActivity : Activity() {
    private lateinit var store: Store
    private lateinit var ui: Ui
    private lateinit var mode: Mode
    private var isNew = false
    private var locked = false
    private var nameField: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        val existing = intent.getStringExtra("id")?.let { store.get(it) }
        isNew = existing == null
        mode = existing ?: Mode(quick = intent.getBooleanExtra("quick", false)).also {
            if (it.quick) it.quickUntil = System.currentTimeMillis() + 60 * 60_000L
        }
        locked = !isNew && mode.isActive()
        title = when {
            mode.quick -> "Quick block"
            isNew -> "New mode"
            else -> "Edit mode"
        }
        ui = Ui(this, screen())
        render()
    }

    private fun syncName() {
        nameField?.let { mode.name = it.text.toString().trim() }
    }

    private fun render() {
        syncName()
        ui.clear()
        nameField = null
        val editable = !locked

        if (locked) {
            ui.text(
                "🔒 This is blocking right now, until ${mode.untilText()}. You can edit it after that, " +
                    "or turn it off from the main screen with an emergency unlock."
            )
        }

        if (mode.quick) {
            renderQuick()
            return
        } else {
            // Emoji + name
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, 0)
            }
            row.addView(Button(this).apply {
                text = mode.emoji
                textSize = 28f
                isEnabled = editable
                setOnClickListener { pickEmoji() }
            }, LinearLayout.LayoutParams(dp(76), dp(76)))
            val field = EditText(this).apply {
                setText(mode.name)
                hint = "Mode name, e.g. Work"
                textSize = 18f
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                isEnabled = editable
            }
            row.addView(field, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            })
            ui.add(row)
            nameField = field

            ui.header("Apps")
            ui.button(appsText(this, mode.apps), editable) { pickApps(single = false) }

            ui.header("When")
            val atTimes = RadioButton(this).apply {
                id = View.generateViewId()
                text = "At set times"
                isEnabled = editable
            }
            val afterWake = RadioButton(this).apply {
                id = View.generateViewId()
                text = "After I wake up (first unlock of the day)"
                isEnabled = editable
            }
            val group = RadioGroup(this).apply {
                addView(atTimes)
                addView(afterWake)
                check(if (mode.kind == KIND_WAKE) afterWake.id else atTimes.id)
                setOnCheckedChangeListener { _, checked ->
                    mode.kind = if (checked == afterWake.id) KIND_WAKE else KIND_SCHEDULE
                    ui.root.post { render() }
                }
            }
            ui.add(group)
        }

        ui.button("Days: ${daysText(mode.days)}", editable) { pickDays() }

        if (mode.kind == KIND_SCHEDULE) {
            ui.button("From ${fmt(mode.start)}", editable) { pickTime(mode.start) { mode.start = it } }
            ui.button("Until ${fmt(mode.end)}", editable) { pickTime(mode.end) { mode.end = it } }
            if (mode.end < mode.start) ui.text("Ends the next morning.", dim = true)
        } else {
            ui.text("Starts the first time you unlock your phone after the time below.", dim = true)
            ui.button("Counts from ${fmt(mode.wakeAfter)}", editable) { pickTime(mode.wakeAfter) { mode.wakeAfter = it } }
            ui.button("Lasts ${mode.wakeMinutes} min", editable) { pickDuration() }
        }

        ui.header("Emergency unlock")
        ui.button("Countdown: ${waitText(mode.emergencyWait)}", editable) { pickWait() }

        ui.text("", size = 8f)
        ui.button("Save", editable) { save() }
        if (!isNew) {
            ui.button("Delete", editable) {
                AlertDialog.Builder(this)
                    .setTitle("Delete ${displayName(this, mode)}?")
                    .setPositiveButton("Delete") { _, _ ->
                        store.delete(mode.id)
                        finish()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    /** Quick block: apps + "until when", like setting a timer. */
    private fun renderQuick() {
        ui.header("Apps")
        ui.button(appsText(this, mode.apps)) { pickApps(single = false) }

        ui.header("Block until")
        ui.text("${untilLabel(mode.quickUntil)}  ·  ${leftText(mode.quickUntil)}", size = 20f, bold = true)

        val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((label, minutes) in listOf("30 min" to 30, "1 h" to 60, "2 h" to 120, "4 h" to 240)) {
            presets.addView(Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener {
                    mode.quickUntil = System.currentTimeMillis() + minutes * 60_000L
                    render()
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        ui.add(presets)

        ui.button("Pick a time…") {
            val shown = LocalDateTime.now().plusHours(1)
            TimePickerDialog(this, { _, h, m ->
                val now = LocalDateTime.now()
                var end = now.toLocalDate().atTime(h, m)
                if (!end.isAfter(now)) end = end.plusDays(1) // a time that has passed means tomorrow
                mode.quickUntil = end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                render()
            }, shown.hour, 0, true).show()
        }

        ui.text("Starts right away and disappears when it ends. Ending it early needs an emergency unlock.", dim = true)
        ui.text("", size = 8f)
        ui.button("Start block") { save() }
    }

    private fun save() {
        syncName()
        if (mode.quick) {
            if (mode.apps.isEmpty()) return toast("Choose at least one app")
            if (mode.quickUntil <= System.currentTimeMillis()) return toast("Pick a time in the future")
            store.save(mode)
            finish()
            return
        }
        if (!isNew && store.get(mode.id)?.isActive() == true) {
            toast("It started blocking, so it can't be changed right now")
            return
        }
        if (mode.apps.isEmpty()) return toast("Choose at least one app")
        if (mode.days.isEmpty()) return toast("Choose at least one day")
        if (mode.kind == KIND_SCHEDULE && mode.start == mode.end) return toast("Start and end can't be the same time")
        if (!mode.quick && mode.name.isBlank()) mode.name = "My mode"

        // Don't start a wake-up mode later today if today's wake-up has already passed.
        if (mode.kind == KIND_WAKE) {
            val now = LocalDateTime.now()
            if (now.hour * 60 + now.minute >= mode.wakeAfter) mode.wakeDate = today()
        }
        store.save(mode)
        finish()
    }

    // ---- Pickers ----

    private fun pickApps(single: Boolean) {
        syncName()
        startActivityForResult(
            Intent(this, AppPickerActivity::class.java)
                .putExtra("selected", mode.apps.toTypedArray())
                .putExtra("single", single),
            1
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            data?.getStringArrayExtra("selected")?.let { mode.apps = it.toSet() }
            render()
        }
    }

    private fun pickDays() {
        val names = (1..7).map { DayOfWeek.of(it).getDisplayName(TextStyle.FULL, Locale.getDefault()) }.toTypedArray()
        val checked = BooleanArray(7) { (it + 1) in mode.days }
        AlertDialog.Builder(this)
            .setTitle("Days")
            .setMultiChoiceItems(names, checked) { _, i, c -> checked[i] = c }
            .setPositiveButton("OK") { _, _ ->
                mode.days = (1..7).filter { checked[it - 1] }.toSet()
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickTime(current: Int, set: (Int) -> Unit) {
        TimePickerDialog(this, { _, h, m ->
            set(h * 60 + m)
            render()
        }, current / 60, current % 60, true).show()
    }

    private fun pickDuration() {
        val options = intArrayOf(15, 30, 45, 60, 90, 120, 180)
        AlertDialog.Builder(this)
            .setTitle("Block for")
            .setItems(options.map { if (it < 60 || it % 60 != 0) "$it min" else "${it / 60} h" }.toTypedArray()) { _, i ->
                mode.wakeMinutes = options[i]
                render()
            }
            .show()
    }

    private fun pickWait() {
        val picker = NumberPicker(this).apply {
            minValue = 0
            maxValue = MAX_WAIT_SECONDS
            value = mode.emergencyWait.coerceIn(0, MAX_WAIT_SECONDS)
            wrapSelectorWheel = false
        }
        AlertDialog.Builder(this)
            .setTitle("Countdown before unlocking (seconds)")
            .setView(picker)
            .setPositiveButton("OK") { _, _ ->
                picker.clearFocus() // keeps a number typed on the keyboard
                mode.emergencyWait = picker.value
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickEmoji() {
        lateinit var dialog: AlertDialog
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }
        val grid = GridLayout(this).apply { columnCount = 6 }
        for (e in EMOJIS) {
            grid.addView(TextView(this).apply {
                text = e
                textSize = 28f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(6), dp(6), dp(6))
                setOnClickListener {
                    mode.emoji = e
                    dialog.dismiss()
                    render()
                }
            })
        }
        box.addView(grid, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL })
        val custom = EditText(this).apply { hint = "Or type any emoji" }
        box.addView(custom)

        dialog = AlertDialog.Builder(this)
            .setTitle("Pick an icon")
            .setView(android.widget.ScrollView(this).apply { addView(box) })
            .setPositiveButton("Use typed emoji") { _, _ ->
                firstCharacter(custom.text.toString().trim())?.let {
                    mode.emoji = it
                    render()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }

    /** First visible character, so multi-part emoji like 👨‍💻 stay whole. */
    private fun firstCharacter(s: String): String? {
        if (s.isEmpty()) return null
        val chars = BreakIterator.getCharacterInstance()
        chars.setText(s)
        return s.substring(0, chars.next())
    }

    companion object {
        val EMOJIS = listOf(
            "💼", "💻", "📚", "✍️", "🎯", "🧠",
            "🌅", "☀️", "🌙", "😴", "🛏️", "☕",
            "🧘", "🏃", "💪", "🚶", "🍽️", "🏠",
            "👨‍👩‍👧", "❤️", "🎨", "🎧", "📖", "🌿",
            "🔒", "⏰", "📵", "🚫", "🔥", "⭐",
        )
    }
}
