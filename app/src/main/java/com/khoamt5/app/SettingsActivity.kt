package com.khoamt5.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Cài đặt: các quyền cần cấp và trạng thái hai lớp chặn. */
class SettingsActivity : Activity() {

    private class Row(
        val dot: View,
        val okText: TextView,
        val action: TextView,
        val check: () -> Boolean
    )

    private val rows = ArrayList<Row>()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var layer1: TextView
    private var builtDark = false
    private lateinit var layer2: TextView

    private val tick = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.setupWindow(this)
        builtDark = Ui.dark
        val c = this
        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL

        root.addView(Ui.link(c, "‹ Quay lại") { finish() })
        root.addView(Ui.text(c, "Cài đặt & quyền", 26f, Ui.TEXT, true), Ui.lp(c, 6))
        root.addView(
            Ui.text(
                c,
                "Cấp đủ các quyền dưới đây để khóa không bị hở. Mục nào cấp xong sẽ chuyển sang xanh.",
                14f, Ui.MUTED
            ),
            Ui.lp(c, 4)
        )

        val card = Ui.card(c)
        addRow(
            card, "Truy cập sử dụng", "Để lớp 2 biết app nào đang mở",
            { Perms.usage(this) },
            { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        )
        addRow(
            card, "Hiện trên app khác", "Để che màn hình khi mở app bị khóa",
            { Perms.overlay(this) },
            {
                open(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + packageName)
                    )
                )
            }
        )
        addRow(
            card, "Thông báo", "Dịch vụ giám sát cần một thông báo cố định",
            { Perms.notif(this) },
            {
                if (Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
                } else {
                    open(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    )
                }
            }
        )
        addRow(
            card, "Không hạn chế pin", "Để hệ thống không tắt Block khi đang khóa",
            { Perms.battery(this) },
            {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + packageName)
                        )
                    )
                } catch (_: Exception) {
                    open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        )
        addRow(
            card, "Trợ năng", "Lớp 1: biết khi một app khác vừa mở",
            { Perms.accessibility(this) },
            { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        )
        root.addView(card, Ui.lp(c, 18))

        // Giao diện
        root.addView(Ui.text(c, "Giao diện", 13f, Ui.MUTED, true), Ui.lp(c, 22))
        val look = Ui.card(c)
        look.addView(Ui.text(c, "Chế độ nền", 12f, Ui.MUTED))
        look.addView(
            Ui.segment(c, arrayOf("Tự động", "Sáng", "Tối"), LockStore.themeMode(c)) { i ->
                LockStore.setThemeMode(c, i)
                recreate()
            },
            Ui.lp(c, 8)
        )
        look.addView(Ui.text(c, "Màu nền", 12f, Ui.MUTED), Ui.lp(c, 14))
        look.addView(
            Ui.segment(c, arrayOf("Theo trạng thái", "Chuyển màu từ từ"), LockStore.colorMode(c)) { i ->
                LockStore.setColorMode(c, i)
                Ui.refreshMode(c)
                recreate()
            },
            Ui.lp(c, 8)
        )
        root.addView(look, Ui.lp(c, 8))

        // Trạng thái hai lớp
        root.addView(Ui.text(c, "Trạng thái hai lớp chặn", 13f, Ui.MUTED, true), Ui.lp(c, 22))
        val sc = Ui.card(c)
        layer1 = Ui.text(c, "", 14f, Ui.TEXT)
        layer2 = Ui.text(c, "", 14f, Ui.TEXT)
        sc.addView(layer1)
        sc.addView(layer2, Ui.lp(c, 10))
        root.addView(sc, Ui.lp(c, 8))

        // Mẹo
        root.addView(Ui.text(c, "Mẹo", 13f, Ui.MUTED, true), Ui.lp(c, 22))
        val tips = Ui.card(c)
        tips.addView(
            Ui.text(
                c,
                "• Công tắc Trợ năng bị mờ: Cài đặt → Ứng dụng → Block → dấu ⋮ → " +
                    "Cho phép cài đặt bị hạn chế, rồi bật lại.",
                13f, Ui.MUTED
            )
        )
        tips.addView(
            Ui.text(
                c,
                "• Xiaomi, Oppo, Realme, Vivo: cho phép tự khởi động và khóa thẻ app trong màn hình đa nhiệm.",
                13f, Ui.MUTED
            ),
            Ui.lp(c, 8)
        )
        tips.addView(
            Ui.text(c, "• Khóa app không đóng lệnh MT5 đang mở. Mỗi lệnh phải có SL.", 13f, Ui.MUTED),
            Ui.lp(c, 8)
        )
        root.addView(tips, Ui.lp(c, 8))

        setContentView(Ui.screen(c, root))
    }

    override fun onResume() {
        super.onResume()
        Ui.refreshMode(this)
        if (Ui.dark != builtDark) {
            recreate()
            return
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Không mở được trang này", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addRow(
        card: LinearLayout, title: String, desc: String,
        check: () -> Boolean, onGrant: () -> Unit
    ) {
        val c = this
        if (card.childCount > 0) card.addView(Ui.divider(c))
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 10))

        val dot = Ui.dot(c, Ui.WARN)
        row.addView(dot)

        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        col.addView(Ui.text(c, title, 15f, Ui.TEXT, true))
        col.addView(Ui.text(c, desc, 12f, Ui.MUTED))
        row.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))

        val okText = Ui.text(c, "Đã bật", 13f, Ui.OK, true)
        row.addView(okText)
        val action = Ui.link(c, "Cấp") { onGrant() }
        row.addView(action)

        card.addView(row)
        rows.add(Row(dot, okText, action, check))
    }

    private fun update() {
        for (r in rows) {
            val ok = r.check()
            Ui.setDot(r.dot, if (ok) Ui.OK else Ui.WARN)
            r.okText.visibility = if (ok) View.VISIBLE else View.GONE
            r.action.visibility = if (ok) View.GONE else View.VISIBLE
        }

        val acc = Perms.accessibility(this)
        var t1 = "Lớp 1 · Trợ năng: " + (if (acc) "bật" else "tắt")
        if (LockStore.accSeen.isNotEmpty()) t1 += "\nVừa thấy mở: " + LockStore.accSeen
        layer1.text = t1

        val alive = System.currentTimeMillis() - LockStore.watchAliveAt < 3000L
        val st = if (alive) "đang chạy"
        else if (Perms.layer2(this)) "sẵn sàng, tự chạy khi bắt đầu khóa"
        else "chưa đủ quyền"
        var t2 = "Lớp 2 · Giám sát nền: " + st
        if (LockStore.watchSeen.isNotEmpty()) t2 += "\nVừa thấy mở: " + LockStore.watchSeen
        layer2.text = t2
    }
}
