package com.khoamt5.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Bảng màu (sáng/tối) và các hàm dựng giao diện dùng chung cho mọi màn hình. */
object Ui {
    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    /** Đang dùng nền tối hay không. Cập nhật bằng refreshMode(). */
    @Volatile var dark: Boolean = false
        private set

    /** Màu nền đổi từ từ (cầu vồng nhẹ) thay vì đổi theo trạng thái. */
    @Volatile var cycle: Boolean = false
        private set

    /** Đọc lại cài đặt giao diện: 0 = tự động theo hệ thống, 1 = sáng, 2 = tối. */
    fun refreshMode(c: Context) {
        val mode = LockStore.themeMode(c)
        dark = when (mode) {
            1 -> false
            2 -> true
            else -> (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
        cycle = LockStore.colorMode(c) == 1
    }

    private fun pick(l: Int, d: Int): Int = if (dark) d else l

    private val L_CARD = Color.parseColor("#FFFFFF")
    private val D_CARD = Color.parseColor("#1B222C")
    private val L_BORDER = Color.parseColor("#E6E8EC")
    private val D_BORDER = Color.parseColor("#2A313C")
    private val L_TEXT = Color.parseColor("#12161C")
    private val D_TEXT = Color.parseColor("#F0F3F6")
    private val L_MUTED = Color.parseColor("#6B7280")
    private val D_MUTED = Color.parseColor("#9AA4B2")
    private val L_LINK = Color.parseColor("#2563EB")
    private val D_LINK = Color.parseColor("#7DB1FF")
    private val L_OK = Color.parseColor("#16A34A")
    private val D_OK = Color.parseColor("#34D399")
    private val L_WARN = Color.parseColor("#D97706")
    private val D_WARN = Color.parseColor("#FBBF24")
    private val L_BASE = Color.parseColor("#FAFBFC")
    private val D_BASE = Color.parseColor("#0D1117")
    private val L_BANNER = Color.parseColor("#FFF7E6")
    private val D_BANNER = Color.parseColor("#3A2E12")
    private val L_BANNER_B = Color.parseColor("#F5D9A8")
    private val D_BANNER_B = Color.parseColor("#6B5420")

    val CARD: Int get() = pick(L_CARD, D_CARD)
    val BORDER: Int get() = pick(L_BORDER, D_BORDER)
    val TEXT: Int get() = pick(L_TEXT, D_TEXT)
    val MUTED: Int get() = pick(L_MUTED, D_MUTED)
    val LINK: Int get() = pick(L_LINK, D_LINK)
    val OK: Int get() = pick(L_OK, D_OK)
    val WARN: Int get() = pick(L_WARN, D_WARN)
    val TRACK: Int get() = pick(L_BORDER, D_BORDER)
    val PAGE_END: Int get() = pick(L_BASE, D_BASE)
    val BANNER_BG: Int get() = pick(L_BANNER, D_BANNER)
    val BANNER_BORDER: Int get() = pick(L_BANNER_B, D_BANNER_B)
    val CARD_FILL: Int get() = if (dark) Color.argb(225, 22, 27, 36) else Color.argb(238, 255, 255, 255)
    val GLASS: Int get() = if (dark) Color.argb(215, 18, 22, 30) else Color.argb(235, 255, 255, 255)

    /** Chữ đặt trên nền màu đảo ngược (chip đang chọn). */
    val ON_TEXT: Int get() = pick(Color.WHITE, L_TEXT)

    val ACCENT = Color.parseColor("#E5484D")
    val TEAL = Color.parseColor("#14B8A6")
    val CORAL = Color.parseColor("#FF6B4A")

    /** Đổi giá trị đã chọn thành nền mới: aurora động nên chỉ cần chỉnh thanh hệ thống. */
    fun applyTheme(a: Activity, v: AuroraView, locked: Boolean) {
        v.locked = locked
        a.window.navigationBarColor = PAGE_END
    }

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()

    fun gradientRounded(c: Context, left: Int, right: Int, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(left, right))
        g.cornerRadius = dp(c, radiusDp).toFloat()
        return g
    }

    fun rounded(c: Context, fill: Int, radiusDp: Int, stroke: Int = 0, strokeDp: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(c, radiusDp).toFloat()
        if (strokeDp > 0) g.setStroke(dp(c, strokeDp), stroke)
        return g
    }

    /** Thanh hệ thống trong suốt, biểu tượng đậm khi nền sáng, sáng khi nền tối. */
    fun setupWindow(a: Activity) {
        refreshMode(a)
        val w = a.window
        w.statusBarColor = Color.TRANSPARENT
        w.navigationBarColor = PAGE_END
        var f = w.decorView.systemUiVisibility or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        val light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        f = if (dark) (f and light.inv()) else (f or light)
        w.decorView.systemUiVisibility = f
    }

    /** Hộp thoại có giao diện sáng hoặc tối theo chế độ hiện tại. */
    fun dialog(a: Activity): AlertDialog.Builder {
        val theme = if (dark) android.R.style.Theme_Material_Dialog_Alert
        else android.R.style.Theme_Material_Light_Dialog_Alert
        return AlertDialog.Builder(a, theme)
    }

    fun text(c: Context, s: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(c)
        t.text = s
        t.textSize = sizeSp
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    fun lp(c: Context, top: Int = 0, width: Int = MATCH, height: Int = WRAP): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(width, height)
        p.topMargin = dp(c, top)
        return p
    }

    fun card(c: Context): LinearLayout {
        val l = LinearLayout(c)
        l.orientation = LinearLayout.VERTICAL
        l.background = rounded(c, CARD_FILL, 20, BORDER, 1)
        l.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14))
        return l
    }

    /** Nút bo tròn. stroke = 0 thì không viền. */
    fun button(
        c: Context, label: String, fill: Int, textColor: Int, stroke: Int = 0, onClick: () -> Unit
    ): TextView {
        val t = TextView(c)
        t.text = label
        t.textSize = 15f
        t.setTextColor(textColor)
        t.setTypeface(t.typeface, Typeface.BOLD)
        t.gravity = Gravity.CENTER
        t.background = rounded(c, fill, 14, stroke, if (stroke != 0) 1 else 0)
        t.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10))
        t.setOnClickListener { onClick() }
        return t
    }

    /** Chữ bấm được, màu xanh. */
    fun link(c: Context, label: String, onClick: () -> Unit): TextView {
        val t = text(c, label, 14f, LINK, true)
        t.setPadding(dp(c, 4), dp(c, 6), dp(c, 4), dp(c, 6))
        t.setOnClickListener { onClick() }
        return t
    }

    /** Hàng chip chọn một trong nhiều giá trị. */
    fun segment(c: Context, labels: Array<String>, selected: Int, onPick: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        for (i in labels.indices) {
            val chip = TextView(c)
            chip.text = labels[i]
            chip.textSize = 13f
            chip.gravity = Gravity.CENTER
            chip.setTypeface(chip.typeface, Typeface.BOLD)
            chip.setPadding(dp(c, 4), dp(c, 10), dp(c, 4), dp(c, 10))
            val on = i == selected
            chip.background = if (on) rounded(c, TEXT, 12) else rounded(c, CARD, 12, BORDER, 1)
            chip.setTextColor(if (on) ON_TEXT else TEXT)
            chip.setOnClickListener { onPick(i) }
            val p = LinearLayout.LayoutParams(0, WRAP, 1f)
            if (i < labels.size - 1) p.rightMargin = dp(c, 8)
            row.addView(chip, p)
        }
        return row
    }

    fun dot(c: Context, col: Int): View {
        val v = View(c)
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(col)
        v.background = g
        val p = LinearLayout.LayoutParams(dp(c, 10), dp(c, 10))
        p.rightMargin = dp(c, 12)
        v.layoutParams = p
        return v
    }

    /** Đổi màu chấm tròn tạo bởi dot(). */
    fun setDot(v: View, col: Int) {
        val g = v.background
        if (g is GradientDrawable) g.setColor(col)
    }

    /** Hộp thoại nhập chữ. resetLabel != null thì có thêm nút phụ (ví dụ "Mặc định"). */
    fun prompt(
        a: Activity, title: String, initial: String, hint: String,
        resetLabel: String? = null, onReset: () -> Unit = {}, onOk: (String) -> Unit
    ) {
        val et = EditText(a)
        et.setText(initial)
        et.hint = hint
        et.setTextColor(TEXT)
        et.setHintTextColor(MUTED)
        et.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        et.minLines = 2
        et.maxLines = 5
        et.setSelection(et.text.length)
        val box = FrameLayout(a)
        box.setPadding(dp(a, 20), dp(a, 8), dp(a, 20), 0)
        box.addView(et, FrameLayout.LayoutParams(MATCH, WRAP))
        val b = dialog(a)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Lưu") { _, _ -> onOk(et.text.toString()) }
            .setNegativeButton("Hủy", null)
        if (resetLabel != null) {
            b.setNeutralButton(resetLabel) { _, _ -> onReset() }
        }
        b.show()
    }

    fun divider(c: Context): View {
        val v = View(c)
        v.setBackgroundColor(BORDER)
        v.layoutParams = LinearLayout.LayoutParams(MATCH, dp(c, 1))
        return v
    }

    /** Bọc nội dung vào màn hình cuộn được, đặt trên nền aurora. */
    fun screen(c: Context, content: LinearLayout): FrameLayout {
        val res = c.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (res > 0) c.resources.getDimensionPixelSize(res) else dp(c, 24)
        content.setPadding(dp(c, 20), dp(c, 12) + statusBar, dp(c, 20), dp(c, 28))
        val s = ScrollView(c)
        s.setBackgroundColor(Color.TRANSPARENT)
        s.isFillViewport = true
        s.addView(content, FrameLayout.LayoutParams(MATCH, WRAP))
        val frame = FrameLayout(c)
        frame.addView(AuroraView(c), FrameLayout.LayoutParams(MATCH, MATCH))
        frame.addView(s, FrameLayout.LayoutParams(MATCH, MATCH))
        return frame
    }
}
