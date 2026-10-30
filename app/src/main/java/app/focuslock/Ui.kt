package app.focuslock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Sets up a scrolling screen and returns the column to add views to. */
fun Activity.screen(): LinearLayout {
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), dp(40))
    }
    setContentView(ScrollView(this).apply { addView(root) })
    return root
}

/** Tiny helper for building simple screens in code. */
class Ui(private val c: Context, val root: LinearLayout) {
    fun clear() = root.removeAllViews()

    fun add(
        v: View,
        lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
    ) = root.addView(v, lp)

    fun text(
        s: String,
        size: Float = 15f,
        bold: Boolean = false,
        dim: Boolean = false,
        center: Boolean = false
    ): TextView = TextView(c).apply {
        text = s
        textSize = size
        setTextColor(c.textColor())
        if (bold) typeface = Typeface.DEFAULT_BOLD
        if (dim) alpha = 0.65f
        if (center) gravity = Gravity.CENTER
        setPadding(0, c.dp(6), 0, c.dp(6))
    }.also { add(it) }

    fun header(s: String): TextView =
        text(s, 20f, bold = true).apply { setPadding(0, c.dp(24), 0, c.dp(4)) }

    fun button(label: String, enabled: Boolean = true, onClick: () -> Unit): Button =
        Button(c).apply {
            text = label
            isAllCaps = false
            isEnabled = enabled
            setOnClickListener { onClick() }
        }.also { add(it, spaced()) }

    /** Full-width layout with a gap above, so stacked buttons don't touch. */
    fun spaced() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = c.dp(8) }
}

/** Go to the home screen (used to leave a blocked app behind). */
fun Activity.goHome() {
    startActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    finish()
}

/** The theme's main text colour (black in light mode, white in dark mode). */
fun Context.textColor(): Int {
    val a = obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
    val color = a.getColor(0, Color.BLACK)
    a.recycle()
    return color
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.cardBackground(): Drawable = GradientDrawable().apply {
    cornerRadius = dp(16).toFloat()
    setColor(0x1A808080) // translucent grey: works in light and dark mode
}

fun Context.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

fun appLabel(c: Context, pkg: String): String = try {
    val pm = c.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
} catch (e: Exception) {
    pkg
}

fun appIcon(c: Context, pkg: String): Drawable? = try {
    c.packageManager.getApplicationIcon(pkg)
} catch (e: Exception) {
    null
}

fun appsText(c: Context, pkgs: Set<String>): String {
    if (pkgs.isEmpty()) return "Choose apps"
    val labels = pkgs.map { appLabel(c, it) }.sorted()
    return if (labels.size <= 3) labels.joinToString(", ")
    else labels.take(3).joinToString(", ") + " +${labels.size - 3}"
}

/** Mode name, or the apps' names for a quick block. */
fun displayName(c: Context, m: Mode): String = when {
    m.quick -> appsText(c, m.apps)
    m.name.isBlank() -> "Untitled mode"
    else -> m.name
}
