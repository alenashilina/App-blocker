package app.focuslock

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/**
 * A list of installed apps with icons. Returns the selection as "selected".
 * With "single" = true, tapping an app picks it and closes the list.
 */
class AppPickerActivity : Activity() {
    private val selected = mutableSetOf<String>()
    private var single = false
    private lateinit var apps: List<Pair<String, String>> // package to name
    private val icons = HashMap<String, Drawable?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        single = intent.getBooleanExtra("single", false)
        selected += intent.getStringArrayExtra("selected") ?: emptyArray()
        title = if (single) "Choose an app" else "Choose apps"

        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = packageManager.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
            .distinctBy { it.first }
            .filter { it.first != packageName }
            .sortedWith(compareBy({ it.first !in selected }, { it.second.lowercase() }))

        val adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(i: Int): Any = apps[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, convertView: View?, parent: ViewGroup?): View {
                val row = convertView as? LinearLayout ?: makeRow()
                val (pkg, label) = apps[i]
                (row.getChildAt(0) as ImageView).setImageDrawable(icons.getOrPut(pkg) { appIcon(this@AppPickerActivity, pkg) })
                (row.getChildAt(1) as TextView).text = label
                (row.getChildAt(2) as CheckBox).isChecked = pkg in selected
                return row
            }
        }

        val list = ListView(this)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, i, _ ->
            val pkg = apps[i].first
            if (single) {
                selected.clear()
                selected += pkg
                finish()
            } else {
                if (!selected.remove(pkg)) selected += pkg
                adapter.notifyDataSetChanged()
            }
        }
        setContentView(list)
    }

    private fun makeRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(10), dp(16), dp(10))
        addView(ImageView(this@AppPickerActivity), LinearLayout.LayoutParams(dp(36), dp(36)))
        addView(TextView(this@AppPickerActivity).apply {
            setTextColor(textColor())
            textSize = 16f
            setPadding(dp(14), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(CheckBox(this@AppPickerActivity).apply {
            isClickable = false
            isFocusable = false
            if (single) visibility = View.INVISIBLE
        })
    }

    override fun finish() {
        setResult(RESULT_OK, Intent().putExtra("selected", selected.toTypedArray()))
        super.finish()
    }
}
