package com.shilapi.xcertplay

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.shilapi.xcertplay.host.R

/** Update prompts follow the application's navy/cyan visual language on Android 4.4+. */
internal object DiPlayUpdateDialogs {
    private val PANEL = Color.rgb(18, 31, 51)
    private val ACCENT = Color.rgb(42, 202, 241)
    private val MUTED = Color.rgb(177, 193, 213)

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density + 0.5f).toInt()

    private fun drawable(activity: Activity, color: Int, radius: Int, stroke: Boolean = false) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(activity, radius).toFloat()
            if (stroke) setStroke(dp(activity, 1), ACCENT)
        }

    private fun label(activity: Activity, value: String, size: Float, color: Int,
                      bold: Boolean = false) = TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    private fun action(activity: Activity, value: String, primary: Boolean, click: () -> Unit) =
        Button(activity).apply {
            text = value
            isAllCaps = false
            textSize = 15f
            minHeight = 0
            minWidth = 0
            setTextColor(if (primary) Color.rgb(0, 24, 39) else Color.WHITE)
            background = drawable(activity, if (primary) ACCENT else Color.rgb(34, 56, 77),
                12, !primary)
            setOnClickListener { click() }
        }

    private fun panel(activity: Activity, title: String): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 20), dp(activity, 20), dp(activity, 18))
            background = drawable(activity, PANEL, 20, true)
            addView(label(activity, title, 21f, ACCENT, true),
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(activity, 14)
                })
        }

    private fun show(activity: Activity, content: LinearLayout, cancellable: Boolean = true): Dialog {
        val dialog = Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(cancellable)
            setContentView(content)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.attributes = window!!.attributes.apply { dimAmount = 0.65f }
        }
        dialog.show()
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.92f).toInt()
                .coerceAtMost(dp(activity, 540)),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        return dialog
    }

    fun confirm(activity: Activity, title: String, message: String,
                primaryLabel: String, secondaryLabel: String? = null,
                onPrimary: () -> Unit): Dialog {
        val root = panel(activity, title)
        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            addView(label(activity, message, 15f, MUTED).apply { setTextIsSelectable(true) })
        }
        // Release notes can be several pages long; never let a dialog exceed the car display.
        val availableHeight = activity.resources.displayMetrics.heightPixels
        val messageHeight = if (message.length > 160)
            (availableHeight * 0.42f).toInt().coerceAtLeast(dp(activity, 64))
        else ViewGroup.LayoutParams.WRAP_CONTENT
        root.addView(scroll, LinearLayout.LayoutParams(-1, messageHeight).apply {
            bottomMargin = dp(activity, 18)
        })
        var dialog: Dialog? = null
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        if (secondaryLabel != null) {
            row.addView(action(activity, secondaryLabel, false) { dialog?.dismiss() },
                LinearLayout.LayoutParams(0, dp(activity, 48), 1f).apply {
                    rightMargin = dp(activity, 8)
                })
        }
        row.addView(action(activity, primaryLabel, true) {
            dialog?.dismiss()
            onPrimary()
        }, LinearLayout.LayoutParams(0, dp(activity, 48), 1f))
        root.addView(row)
        dialog = show(activity, root)
        return dialog
    }

    class ProgressHandle(val dialog: Dialog, private val bar: ProgressBar,
                         private val percent: TextView, private val phase: TextView) {
        fun update(value: Int) {
            bar.progress = value.coerceIn(0, 100)
            percent.text = "${value.coerceIn(0, 100)}%"
        }
        fun verifying(activity: Activity) {
            phase.text = activity.getString(R.string.update_verifying)
            bar.isIndeterminate = true
            percent.text = ""
        }
        fun dismiss() = dialog.dismiss()
    }

    fun progress(activity: Activity, cancel: () -> Unit): ProgressHandle {
        val root = panel(activity, activity.getString(R.string.update_downloading))
        val phase = label(activity, activity.getString(R.string.update_downloading_phase),
            14f, MUTED)
        root.addView(phase, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(activity, 10)
        })
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                progressTintList = ColorStateList.valueOf(ACCENT)
                progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(44, 62, 83))
            } else {
                @Suppress("DEPRECATION")
                progressDrawable.setColorFilter(ACCENT, android.graphics.PorterDuff.Mode.SRC_IN)
            }
        }
        row.addView(bar, LinearLayout.LayoutParams(0, dp(activity, 10), 1f))
        val percent = label(activity, "0%", 15f, Color.WHITE, true).apply {
            gravity = Gravity.END
        }
        row.addView(percent, LinearLayout.LayoutParams(dp(activity, 54), -2))
        root.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(activity, 18)
        })
        var dialog: Dialog? = null
        root.addView(action(activity, activity.getString(android.R.string.cancel), false) {
            cancel()
            dialog?.dismiss()
        }, LinearLayout.LayoutParams(-1, dp(activity, 48)))
        dialog = show(activity, root, false)
        return ProgressHandle(dialog, bar, percent, phase)
    }
}
