package app.focuslock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

const val KIND_SCHEDULE = 0 // blocks between two times
const val KIND_WAKE = 1     // blocks for N minutes after the first unlock of the day

val ALL_DAYS = (1..7).toSet()   // 1 = Monday … 7 = Sunday
val WEEKDAYS = (1..5).toSet()

/** The sentence you must type for an emergency unlock. */
const val EMERGENCY_PHRASE = "I really need this and I accept breaking my focus"
const val EMERGENCY_WAIT_SECONDS = 30 // default; each mode can set its own
const val MAX_WAIT_SECONDS = 120

/**
 * A mode (emoji, name, apps, schedule) or a quick block (quick = true: apps blocked from now
 * until quickUntil, then it's deleted).
 * Times are minutes since midnight.
 */
class Mode(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var emoji: String = "🎯",
    var quick: Boolean = false,
    var enabled: Boolean = true,
    var offDate: String = "",       // "off just for today": the date it's off
    var kind: Int = KIND_SCHEDULE,
    var days: Set<Int> = WEEKDAYS,
    var start: Int = 9 * 60,
    var end: Int = 18 * 60,
    var wakeAfter: Int = 4 * 60,    // first unlock at/after this time counts as waking up
    var wakeMinutes: Int = 60,
    var wakeDate: String = "",      // date the wake block last started
    var wakeUntil: Long = 0L,
    var apps: Set<String> = emptySet(),
    var quickUntil: Long = 0L,      // quick block: when it ends (then it disappears)
    var emergencyWait: Int = EMERGENCY_WAIT_SECONDS,
) {
    /** Switched on, and not "off for today". */
    val isOn: Boolean get() = enabled && offDate != today()

    /** Blocking right now. */
    fun isActive(now: LocalDateTime = LocalDateTime.now()): Boolean {
        if (quick) return apps.isNotEmpty() && System.currentTimeMillis() < quickUntil
        if (!isOn || apps.isEmpty()) return false
        return if (kind == KIND_WAKE) System.currentTimeMillis() < wakeUntil else inWindow(now)
    }

    private fun inWindow(now: LocalDateTime): Boolean {
        val m = now.hour * 60 + now.minute
        val day = now.dayOfWeek.value
        return if (start <= end) {
            day in days && m >= start && m < end
        } else {
            // Overnight, e.g. 22:00–07:00: the start day decides.
            (day in days && m >= start) || (now.minusDays(1).dayOfWeek.value in days && m < end)
        }
    }

    fun untilText(): String = when {
        quick -> untilLabel(quickUntil)
        kind == KIND_WAKE -> fmtMillis(wakeUntil)
        else -> fmt(end)
    }

    fun scheduleText(): String {
        if (quick) return "Until ${untilLabel(quickUntil)} · ${leftText(quickUntil)}"
        val time = if (kind == KIND_WAKE) "after waking, $wakeMinutes min" else "${fmt(start)}–${fmt(end)}"
        return "${daysText(days)} · $time"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("emoji", emoji); put("quick", quick)
        put("enabled", enabled); put("offDate", offDate); put("kind", kind)
        put("days", JSONArray(days.toList())); put("start", start); put("end", end)
        put("wakeAfter", wakeAfter); put("wakeMinutes", wakeMinutes)
        put("wakeDate", wakeDate); put("wakeUntil", wakeUntil)
        put("apps", JSONArray(apps.toList()))
        put("quickUntil", quickUntil); put("emergencyWait", emergencyWait)
    }

    companion object {
        fun fromJson(o: JSONObject) = Mode(
            id = o.getString("id"),
            name = o.optString("name"),
            emoji = o.optString("emoji", "🎯"),
            quick = o.optBoolean("quick"),
            enabled = o.optBoolean("enabled", true),
            offDate = o.optString("offDate"),
            kind = o.optInt("kind"),
            days = o.optJSONArray("days").ints().toSet(),
            start = o.optInt("start", 9 * 60),
            end = o.optInt("end", 18 * 60),
            wakeAfter = o.optInt("wakeAfter", 4 * 60),
            wakeMinutes = o.optInt("wakeMinutes", 60),
            wakeDate = o.optString("wakeDate"),
            wakeUntil = o.optLong("wakeUntil"),
            apps = o.optJSONArray("apps").strings().toSet(),
            quickUntil = o.optLong("quickUntil"),
            emergencyWait = o.optInt("emergencyWait", EMERGENCY_WAIT_SECONDS),
        )
    }
}

/** Saves modes and emergency unlocks on the phone. */
class Store(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("focuslock", Context.MODE_PRIVATE)

    fun modes(): MutableList<Mode> {
        val s = p.getString("modes", null) ?: return mutableListOf()
        val a = JSONArray(s)
        val list = (0 until a.length()).map { Mode.fromJson(a.getJSONObject(it)) }
        // Finished quick blocks disappear on their own.
        val now = System.currentTimeMillis()
        val alive = list.filter { !it.quick || it.quickUntil > now }.toMutableList()
        if (alive.size != list.size) saveAll(alive)
        return alive
    }

    private fun saveAll(list: List<Mode>) {
        p.edit().putString("modes", JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    fun get(id: String): Mode? = modes().find { it.id == id }

    fun save(m: Mode) {
        val list = modes()
        val i = list.indexOfFirst { it.id == m.id }
        if (i >= 0) list[i] = m else list.add(m)
        saveAll(list)
    }

    fun delete(id: String) = saveAll(modes().filter { it.id != id })

    /** Called on every unlock: starts "after I wake up" modes on the first unlock of the day. */
    fun onUnlock() {
        val now = LocalDateTime.now()
        val today = today()
        val minutes = now.hour * 60 + now.minute
        val list = modes()
        var changed = false
        for (m in list) {
            if (m.kind == KIND_WAKE && m.isOn && m.wakeDate != today &&
                minutes >= m.wakeAfter && now.dayOfWeek.value in m.days
            ) {
                m.wakeDate = today
                m.wakeUntil = System.currentTimeMillis() + m.wakeMinutes * 60_000L
                changed = true
            }
        }
        if (changed) saveAll(list)
    }

    private fun unlocks() = JSONObject(p.getString("unlocks", "{}") ?: "{}")

    fun unlockApp(pkg: String, minutes: Int) {
        val o = unlocks().put(pkg, System.currentTimeMillis() + minutes * 60_000L)
        p.edit().putString("unlocks", o.toString()).apply()
    }

    private fun isUnlocked(pkg: String) = unlocks().optLong(pkg, 0L) > System.currentTimeMillis()

    /** True only the first time it's asked: used to show the guide on first launch. */
    fun firstLaunch(): Boolean {
        if (p.getBoolean("seen_help", false)) return false
        p.edit().putBoolean("seen_help", true).apply()
        return true
    }

    /** Seconds to wait for an emergency unlock of this app: the longest of the modes blocking it. */
    fun waitFor(pkg: String): Int {
        val now = LocalDateTime.now()
        return modes().filter { pkg in it.apps && it.isActive(now) }
            .maxOfOrNull { it.emergencyWait } ?: EMERGENCY_WAIT_SECONDS
    }

    /** The mode blocking this app right now, or null if it may open. */
    fun blockingMode(pkg: String): Mode? {
        if (isUnlocked(pkg)) return null
        val now = LocalDateTime.now()
        return modes().firstOrNull { pkg in it.apps && it.isActive(now) }
    }
}

// ---- Small helpers ----

fun today(): String = LocalDate.now().toString()

fun fmt(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

fun fmtMillis(ms: Long): String {
    val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime()
    return fmt(t.hour * 60 + t.minute)
}

/** "15:30", or "tomorrow 09:00" if it's not today. */
fun untilLabel(ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    return if (d == LocalDate.now()) fmtMillis(ms) else "tomorrow ${fmtMillis(ms)}"
}

/** "1 h 15 min left" */
fun leftText(ms: Long): String {
    val total = ((ms - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L) + 1
    val h = total / 60
    val m = total % 60
    return when {
        h == 0L -> "$m min left"
        m == 0L -> "$h h left"
        else -> "$h h $m min left"
    }
}

fun waitText(seconds: Int) = if (seconds <= 0) "none" else "$seconds sec"

fun daysText(days: Set<Int>): String = when (days) {
    ALL_DAYS -> "Every day"
    WEEKDAYS -> "Mon–Fri"
    setOf(6, 7) -> "Weekends"
    emptySet<Int>() -> "No days"
    else -> days.sorted().joinToString(", ") {
        DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }
}

private fun JSONArray?.ints(): List<Int> =
    if (this == null) emptyList() else (0 until length()).map { getInt(it) }

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { getString(it) }
