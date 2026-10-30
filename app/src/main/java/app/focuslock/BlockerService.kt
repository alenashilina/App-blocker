package app.focuslock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * Watches which app comes to the front. If a mode is blocking it,
 * sends you to the home screen and shows the block screen.
 */
class BlockerService : AccessibilityService() {
    private lateinit var store: Store
    private var current: String? = null
    private var lastBlocked: String? = null
    private var lastBlockedAt = 0L
    private var keyboards: Set<String> = emptySet()
    private val handler = Handler(Looper.getMainLooper())

    // Re-checks every 30 s: closes an app left open when a block starts
    // or when an emergency unlock runs out.
    private val tick = object : Runnable {
        override fun run() {
            current?.let { check(it) }
            handler.postDelayed(this, 30_000)
        }
    }

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            store.onUnlock()
            current?.let { check(it) }
        }
    }

    override fun onServiceConnected() {
        store = Store(this)
        keyboards = (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .enabledInputMethodList.map { it.packageName }.toSet()
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(unlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(unlockReceiver, filter)
        }
        handler.post(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // The notification shade and the keyboard aren't "the app you're in".
        if (pkg == "com.android.systemui" || pkg in keyboards) return
        current = pkg
        check(pkg)
    }

    private fun check(pkg: String) {
        if (pkg == packageName) return
        val mode = store.blockingMode(pkg) ?: return
        // Apps fire several events while opening; show the block screen once.
        val now = System.currentTimeMillis()
        if (pkg == lastBlocked && now - lastBlockedAt < 2000) return
        lastBlocked = pkg
        lastBlockedAt = now
        // Show the block screen on top of the app. (Pressing Home first would stop
        // Android from opening it for a few seconds.)
        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra("pkg", pkg)
                .putExtra("modeId", mode.id)
        )
        // Backup: if the block screen didn't appear, at least leave the app.
        handler.postDelayed({
            if (current == pkg) performGlobalAction(GLOBAL_ACTION_HOME)
        }, 1500)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        try {
            unregisterReceiver(unlockReceiver)
        } catch (e: Exception) {
            // Was never registered.
        }
        super.onDestroy()
    }
}
