package com.khoamt5.app

import android.content.Context
import android.graphics.PixelFormat
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Màn hình che dùng chung cho cả hai lớp chặn: aurora, đồng hồ nước, lời nhắc.
 * Chỉ có một cửa sổ tại một thời điểm. Bên nào tạo ra nó (owner) thì bên đó gỡ.
 * Hiện đủ HOLD_MS thì gọi onTimeUp để đẩy về màn hình chính.
 */
object BlockOverlay {
    const val OWNER_NONE = 0
    const val OWNER_ACC = 1
    const val OWNER_WATCH = 2
    const val HOLD_MS = 7000L

    var owner: Int = OWNER_NONE
        private set

    private var root: FrameLayout? = null
    private var wm: WindowManager? = null
    private var ring: RingView? = null
    private var reminder: TextView? = null
    private var hint: TextView? = null
    private var shownAt = 0L
    private var homeAction: (() -> Unit)? = null

    /** Trả về false nếu không hiện được cửa sổ (khi đó người gọi nên đẩy về màn hình chính ngay). */
    fun show(ctx: Context, type: Int, remainingMs: Long, who: Int, onTimeUp: () -> Unit): Boolean {
        if (root == null) {
            if (!create(ctx, type)) return false
            owner = who
            shownAt = SystemClock.elapsedRealtime()
        }
        val r = ring
        if (r != null) {
            val total = LockStore.totalMs(ctx).coerceAtLeast(1L)
            r.ringColor = Ui.CORAL
            r.progress = remainingMs.toFloat() / total.toFloat()
            r.centerText = LockStore.formatMs(remainingMs)
            r.subText = "còn lại"
        }
        homeAction = onTimeUp
        reminder?.text = LockStore.reminder(ctx)
        val left = HOLD_MS - (SystemClock.elapsedRealtime() - shownAt)
        if (left > 0L) {
            hint?.text = "Tự thoát sau " + ((left + 999L) / 1000L) + "s"
        } else {
            hint?.text = "Đang thoát…"
            onTimeUp()
        }
        return true
    }

    fun hide() {
        val v = root
        if (v != null) {
            try {
                wm?.removeView(v)
            } catch (_: Exception) {
            }
        }
        root = null
        wm = null
        ring = null
        reminder = null
        hint = null
        homeAction = null
        owner = OWNER_NONE
    }

    private fun create(ctx: Context, type: Int): Boolean {
        if (type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY &&
            !Settings.canDrawOverlays(ctx)
        ) return false
        val manager = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        Ui.refreshMode(ctx)

        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        val pad = Ui.dp(ctx, 28)
        box.setPadding(pad, pad, pad, pad)

        val tag = Ui.text(ctx, "ĐANG KHÓA", 14f, Ui.ACCENT, true)
        tag.gravity = Gravity.CENTER
        tag.letterSpacing = 0.15f
        box.addView(tag, Ui.lp(ctx, 0))

        val ringView = RingView(ctx)
        box.addView(ringView, Ui.lp(ctx, 12, Ui.dp(ctx, 260), Ui.dp(ctx, 260)))

        val rem = Ui.text(ctx, "", 22f, Ui.TEXT, true)
        rem.gravity = Gravity.CENTER
        box.addView(rem, Ui.lp(ctx, 20))

        val h = Ui.text(ctx, "", 14f, Ui.MUTED)
        h.gravity = Gravity.CENTER
        box.addView(h, Ui.lp(ctx, 20))

        val frame = FrameLayout(ctx)
        val aurora = AuroraView(ctx)
        aurora.locked = true
        frame.addView(aurora, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        frame.addView(box, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))

        // Nút nhỏ để thoát ngay
        val exit = TextView(ctx)
        exit.text = "×"
        exit.textSize = 26f
        exit.gravity = Gravity.CENTER
        exit.setTextColor(Ui.TEXT)
        exit.background = Ui.rounded(ctx, Ui.CARD_FILL, 22, Ui.BORDER, 1)
        exit.setOnClickListener { homeAction?.invoke() }
        val ep = FrameLayout.LayoutParams(Ui.dp(ctx, 44), Ui.dp(ctx, 44))
        ep.gravity = Gravity.TOP or Gravity.END
        ep.topMargin = Ui.dp(ctx, 52)
        ep.rightMargin = Ui.dp(ctx, 20)
        frame.addView(exit, ep)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        )
        try {
            manager.addView(frame, lp)
        } catch (_: Exception) {
            return false
        }
        root = frame
        wm = manager
        ring = ringView
        reminder = rem
        hint = h
        return true
    }
}
