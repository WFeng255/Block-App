package com.khoamt5.app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Màn hình chính: vòng đếm ngược, chọn thời gian bằng chip, nút khóa,
 * lời nhắc, chuỗi ngày giữ kỷ luật. Quyền và trạng thái nằm ở màn hình Cài đặt.
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastKick = 0L

    private lateinit var aurora: AuroraView
    private lateinit var ring: RingView
    private lateinit var warnDot: View
    private val chips = ArrayList<TextView>()
    private lateinit var appsText: TextView
    private lateinit var selectLink: TextView
    private lateinit var lockBtn: TextView
    private lateinit var noteLink: TextView
    private lateinit var banner: LinearLayout
    private lateinit var reminderText: TextView
    private lateinit var streakNum: TextView
    private lateinit var streakSub: TextView
    private val dayDots = ArrayList<View>()
    private val dayNums = ArrayList<TextView>()
    private lateinit var keepBtn: TextView
    private lateinit var breakBtn: TextView

    private var builtDark = false
    private val chipValues = longArrayOf(15L, 60L, LockStore.END_OF_DAY)
    private var lastChipSig = ""
    private var lastLockedUi: Boolean? = null

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.setupWindow(this)
        builtDark = Ui.dark
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        Ui.refreshMode(this)
        if (Ui.dark != builtDark) {
            recreate()
            return
        }
        updateStreak()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    // ---------- Dựng giao diện ----------

    private fun buildUi(): View {
        val c = this
        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL

        // Đầu trang
        val header = LinearLayout(c)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.addView(Ui.text(c, "Block", 28f, Ui.TEXT, true), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        warnDot = Ui.dot(c, Ui.WARN)
        val wp = LinearLayout.LayoutParams(Ui.dp(c, 10), Ui.dp(c, 10))
        wp.rightMargin = Ui.dp(c, 2)
        warnDot.layoutParams = wp
        header.addView(warnDot)
        header.addView(Ui.link(c, "Cài đặt") { openSettings() })
        root.addView(header)
        root.addView(
            Ui.text(c, "Mất bình tĩnh sau một lệnh thua? Khóa lại, hít thở.", 14f, Ui.MUTED),
            Ui.lp(c, 2)
        )

        // Vòng đếm ngược
        ring = RingView(c)
        val rlp = Ui.lp(c, 20, Ui.dp(c, 250), Ui.dp(c, 250))
        rlp.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(ring, rlp)

        // Chip thời gian
        val chipRow = LinearLayout(c)
        chipRow.orientation = LinearLayout.HORIZONTAL
        for (i in 0 until 4) {
            val chip = TextView(c)
            chip.gravity = Gravity.CENTER
            chip.textSize = 14f
            chip.setTypeface(chip.typeface, android.graphics.Typeface.BOLD)
            chip.setPadding(Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12))
            chip.setOnClickListener { onChip(i) }
            val p = LinearLayout.LayoutParams(0, Ui.WRAP, 1f)
            if (i < 3) p.rightMargin = Ui.dp(c, 8)
            chipRow.addView(chip, p)
            chips.add(chip)
        }
        root.addView(chipRow, Ui.lp(c, 16))

        // App bị khóa
        val appsCard = Ui.card(c)
        appsCard.orientation = LinearLayout.HORIZONTAL
        appsCard.gravity = Gravity.CENTER_VERTICAL
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        col.addView(Ui.text(c, "App bị khóa", 12f, Ui.MUTED))
        appsText = Ui.text(c, "", 15f, Ui.TEXT, true)
        col.addView(appsText)
        appsCard.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        selectLink = Ui.link(c, "Chọn") { pickApps() }
        appsCard.addView(selectLink)
        root.addView(appsCard, Ui.lp(c, 14))

        // Nút khóa
        lockBtn = Ui.button(c, "KHÓA", Ui.ACCENT, Color.WHITE) { onLockClicked() }
        lockBtn.textSize = 17f
        lockBtn.setPadding(Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16))
        root.addView(lockBtn, Ui.lp(c, 14))

        noteLink = Ui.link(c, "Ghi vì sao bạn khóa lần này") { noteCurrent() }
        noteLink.gravity = Gravity.CENTER
        noteLink.visibility = View.GONE
        root.addView(noteLink, Ui.lp(c, 4))

        // Cảnh báo thiếu quyền
        banner = Ui.card(c)
        banner.background = Ui.rounded(c, Ui.BANNER_BG, 16, Ui.BANNER_BORDER, 1)
        banner.addView(Ui.text(c, "Chưa đủ quyền để khóa hoạt động", 14f, Ui.TEXT, true))
        banner.addView(
            Ui.text(c, "Cấp quyền trong Cài đặt trước khi dùng để việc khóa không bị hở.", 13f, Ui.MUTED),
            Ui.lp(c, 2)
        )
        banner.addView(Ui.link(c, "Mở Cài đặt ›") { openSettings() })
        banner.visibility = View.GONE
        root.addView(banner, Ui.lp(c, 14))

        // Lời nhắc
        val rc = Ui.card(c)
        val rh = LinearLayout(c)
        rh.orientation = LinearLayout.HORIZONTAL
        rh.gravity = Gravity.CENTER_VERTICAL
        rh.addView(
            Ui.text(c, "Lời nhắc khi bạn mở app bị khóa", 12f, Ui.MUTED),
            LinearLayout.LayoutParams(0, Ui.WRAP, 1f)
        )
        rh.addView(Ui.link(c, "Sửa") { editReminder() })
        rc.addView(rh)
        reminderText = Ui.text(c, "", 15f, Ui.TEXT)
        rc.addView(reminderText, Ui.lp(c, 2))
        root.addView(rc, Ui.lp(c, 14))

        // Chuỗi ngày kỷ luật
        val sc = Ui.card(c)
        val top = LinearLayout(c)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        streakNum = Ui.text(c, "0", 40f, Ui.TEXT, true)
        top.addView(streakNum)
        val txt = LinearLayout(c)
        txt.orientation = LinearLayout.VERTICAL
        txt.addView(Ui.text(c, "ngày giữ kỷ luật liên tiếp", 14f, Ui.TEXT, true))
        streakSub = Ui.text(c, "", 12f, Ui.MUTED)
        txt.addView(streakSub)
        val tp = LinearLayout.LayoutParams(0, Ui.WRAP, 1f)
        tp.leftMargin = Ui.dp(c, 12)
        top.addView(txt, tp)
        top.addView(Ui.link(c, "Nhật ký ›") { startActivity(Intent(c, HistoryActivity::class.java)) })
        sc.addView(top)

        val days = LinearLayout(c)
        days.orientation = LinearLayout.HORIZONTAL
        for (i in 0 until 7) {
            val cell = LinearLayout(c)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            val d = Ui.dot(c, Ui.TRACK)
            d.layoutParams = LinearLayout.LayoutParams(Ui.dp(c, 14), Ui.dp(c, 14))
            cell.addView(d)
            val n = Ui.text(c, "", 11f, Ui.MUTED)
            n.gravity = Gravity.CENTER
            cell.addView(n, Ui.lp(c, 4))
            days.addView(cell, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            dayDots.add(d)
            dayNums.add(n)
        }
        sc.addView(days, Ui.lp(c, 12))

        sc.addView(Ui.text(c, "Hôm nay bạn đã giữ đúng luật chưa?", 13f, Ui.MUTED), Ui.lp(c, 14))
        val br = LinearLayout(c)
        br.orientation = LinearLayout.HORIZONTAL
        keepBtn = Ui.button(c, "Đã giữ đúng luật", Ui.CARD, Ui.OK, Ui.OK) {
            LockStore.markToday(c, true)
            updateStreak()
        }
        breakBtn = Ui.button(c, "Đã phá luật", Ui.CARD, Ui.ACCENT, Ui.ACCENT) {
            LockStore.markToday(c, false)
            updateStreak()
        }
        val kp = LinearLayout.LayoutParams(0, Ui.WRAP, 1f)
        kp.rightMargin = Ui.dp(c, 8)
        br.addView(keepBtn, kp)
        br.addView(breakBtn, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        sc.addView(br, Ui.lp(c, 8))
        root.addView(sc, Ui.lp(c, 14))

        val frame = Ui.screen(c, root)
        aurora = frame.getChildAt(0) as AuroraView
        return frame
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    // ---------- Cập nhật định kỳ ----------

    private fun refresh() {
        val locked = LockStore.isLocked(this)
        val remaining = LockStore.remainingMs(this)
        val sel = LockStore.selectedMinutes(this)
        val minutes = LockStore.resolveMinutes(sel)

        if (locked) {
            val total = LockStore.totalMs(this).coerceAtLeast(1L)
            ring.ringColor = Ui.CORAL
            ring.progress = remaining.toFloat() / total.toFloat()
            ring.centerText = LockStore.formatMs(remaining)
            ring.subText = "đang khóa"
        } else {
            ring.ringColor = Ui.TEAL
            ring.progress = 0.8f
            ring.centerText = LockStore.formatMs(minutes * 60_000L)
            ring.subText = "sẵn sàng khóa"
        }

        val sig = "$sel|$locked"
        if (sig != lastChipSig) {
            lastChipSig = sig
            updateChips(sel, locked)
        }

        appsText.text = appsSummary()
        selectLink.visibility = if (locked) View.GONE else View.VISIBLE
        noteLink.visibility = if (locked) View.VISIBLE else View.GONE

        if (locked) {
            lockBtn.text = "ĐANG KHÓA · còn " + LockStore.formatMs(remaining)
        } else {
            lockBtn.text = "KHÓA " + LockStore.label(minutes).uppercase()
        }
        if (lastLockedUi != locked) {
            lastLockedUi = locked
            Ui.applyTheme(this, aurora, locked)
            if (locked) {
                lockBtn.background = Ui.rounded(this, Ui.TRACK, 14)
                lockBtn.setTextColor(Ui.MUTED)
            } else {
                lockBtn.background = Ui.gradientRounded(this, Ui.CORAL, Color.parseColor("#FF3D71"), 14)
                lockBtn.setTextColor(Color.WHITE)
            }
        }

        reminderText.text = LockStore.reminder(this)

        val canLock = Perms.accessibility(this) || Perms.layer2(this)
        banner.visibility = if (canLock) View.GONE else View.VISIBLE
        val allGood = Perms.accessibility(this) && Perms.usage(this) && Perms.overlay(this) &&
            Perms.notif(this) && Perms.battery(this)
        warnDot.visibility = if (allGood) View.GONE else View.VISIBLE

        // Đang khóa mà lớp giám sát nền chết thì bật lại.
        if (locked && Perms.usage(this)) {
            val now = System.currentTimeMillis()
            if (now - LockStore.watchAliveAt > 4000L && now - lastKick > 5000L) {
                lastKick = now
                WatcherService.start(this)
            }
        }
    }

    private fun updateChips(sel: Long, locked: Boolean) {
        val labels = arrayOf("15 phút", "1 giờ", "Hết ngày")
        var matched = false
        for (i in 0 until 3) {
            val on = sel == chipValues[i]
            if (on) matched = true
            styleChip(chips[i], labels[i], on, locked)
        }
        val otherLabel = if (matched) "Khác…" else LockStore.label(sel)
        styleChip(chips[3], otherLabel, !matched, locked)
    }

    private fun styleChip(chip: TextView, label: String, selected: Boolean, locked: Boolean) {
        chip.text = label
        chip.background = if (selected) Ui.rounded(this, Ui.TEXT, 14)
        else Ui.rounded(this, Ui.CARD, 14, Ui.BORDER, 1)
        chip.setTextColor(if (selected) Ui.ON_TEXT else Ui.TEXT)
        chip.alpha = if (locked) 0.45f else 1f
    }

    private fun styleChoice(tv: TextView, selected: Boolean, col: Int) {
        tv.background = if (selected) Ui.rounded(this, col, 14)
        else Ui.rounded(this, Ui.CARD, 14, col, 1)
        tv.setTextColor(if (selected) Color.WHITE else col)
    }

    private fun updateStreak() {
        streakNum.text = LockStore.streak(this).toString()
        val today = LockStore.todayMark(this)
        streakSub.text = when (today) {
            true -> "Hôm nay: đã giữ đúng luật"
            false -> "Hôm nay: đã phá luật"
            null -> "Hôm nay: chưa đánh dấu"
        }
        for (i in 0 until 7) {
            val offset = i - 6
            val mark = LockStore.dayMark(this, offset)
            val col = when (mark) {
                true -> Ui.OK
                false -> Ui.ACCENT
                null -> Ui.TRACK
            }
            Ui.setDot(dayDots[i], col)
            dayNums[i].text = LockStore.dayOfMonth(offset).toString()
        }
        styleChoice(keepBtn, today == true, Ui.OK)
        styleChoice(breakBtn, today == false, Ui.ACCENT)
    }

    // ---------- Chọn thời gian ----------

    private fun onChip(i: Int) {
        if (LockStore.isLocked(this)) return
        if (i < 3) {
            LockStore.setSelectedMinutes(this, chipValues[i])
            refresh()
        } else {
            val labels = Array(LockStore.STEPS_MIN.size) { LockStore.label(LockStore.STEPS_MIN[it]) }
            Ui.dialog(this)
                .setTitle("Chọn thời gian khóa")
                .setItems(labels) { _, which ->
                    LockStore.setSelectedMinutes(this, LockStore.STEPS_MIN[which])
                    refresh()
                }
                .show()
        }
    }

    // ---------- Chọn app ----------

    private fun appLabel(pkg: String): String {
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            if (pkg == LockStore.MT5_PACKAGE) "MetaTrader 5" else pkg
        } catch (_: Exception) {
            pkg
        }
    }

    private fun appsSummary(): String {
        val names = LockStore.lockedPackages(this).map { appLabel(it) }.sorted()
        return when {
            names.isEmpty() -> "Chưa chọn app nào"
            names.size <= 2 -> names.joinToString(", ")
            else -> names[0] + ", " + names[1] + " +" + (names.size - 2)
        }
    }

    private fun pickApps() {
        if (LockStore.isLocked(this)) return
        val pm = packageManager
        val protectedPk = LockStore.protectedPackages(this)
        val selected = LockStore.lockedPackages(this)
        val map = LinkedHashMap<String, String>()
        try {
            val infos = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            )
            for (ri in infos) {
                val p = ri.activityInfo.packageName
                if (p in protectedPk) continue
                map[p] = ri.loadLabel(pm).toString()
            }
        } catch (_: Exception) {
        }
        if (map.isEmpty()) {
            Toast.makeText(this, "Không đọc được danh sách app", Toast.LENGTH_SHORT).show()
            return
        }
        val pkgs = map.keys.sortedWith(
            compareBy<String>({ if (it in selected) 0 else 1 }, { map[it]!!.lowercase() })
        )
        val names = Array(pkgs.size) { map[pkgs[it]] ?: pkgs[it] }
        val checked = BooleanArray(pkgs.size) { pkgs[it] in selected }
        Ui.dialog(this)
            .setTitle("Chọn app cần khóa")
            .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Lưu") { _, _ ->
                val result = HashSet<String>()
                for (i in pkgs.indices) {
                    if (checked[i]) result.add(pkgs[i])
                }
                LockStore.setLockedPackages(this, result)
                refresh()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // ---------- Lời nhắc và ghi chú ----------

    private fun editReminder() {
        Ui.prompt(
            this, "Lời nhắc của bạn",
            LockStore.reminder(this),
            "Viết câu bạn muốn đọc khi định mở lại MT5",
            "Mặc định",
            { LockStore.setReminder(this, "") }
        ) { s -> LockStore.setReminder(this, s) }
    }

    private fun noteCurrent() {
        val log = LockStore.readLog(this)
        val cur = if (log.isEmpty()) "" else log[0].note
        Ui.prompt(
            this, "Vì sao bạn khóa?", cur,
            "Ví dụ: vừa âm 2 lệnh liên tiếp, muốn gỡ lại ngay"
        ) { s -> LockStore.setLogNote(this, 0, s) }
    }

    // ---------- Khóa ----------

    private fun onLockClicked() {
        if (LockStore.isLocked(this)) return
        if (!(Perms.accessibility(this) || Perms.layer2(this))) {
            Toast.makeText(this, "Cần cấp quyền trước khi khóa", Toast.LENGTH_SHORT).show()
            openSettings()
            return
        }
        if (LockStore.lockedPackages(this).isEmpty()) {
            Toast.makeText(this, "Hãy chọn ít nhất một app để khóa", Toast.LENGTH_SHORT).show()
            return
        }
        val minutes = LockStore.resolveMinutes(LockStore.selectedMinutes(this))
        if (minutes >= 60L) {
            Ui.dialog(this)
                .setTitle("Khóa " + LockStore.label(minutes) + "?")
                .setMessage("Trong thời gian này các app đã chọn không mở được và không có nút dừng.")
                .setPositiveButton("Khóa") { _, _ -> doLock(minutes) }
                .setNegativeButton("Hủy", null)
                .show()
        } else {
            doLock(minutes)
        }
    }

    private fun doLock(minutes: Long) {
        if (LockStore.isLocked(this)) return
        LockStore.start(this, minutes)
        LockStore.addLog(this, minutes, appsSummary())
        if (Perms.usage(this)) WatcherService.start(this)
        ring.splash()
        lockBtn.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        refresh()
    }
}
