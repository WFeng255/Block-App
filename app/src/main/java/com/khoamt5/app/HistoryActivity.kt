package com.khoamt5.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Nhật ký các lần khóa và thống kê chuỗi ngày kỷ luật. */
class HistoryActivity : Activity() {

    private lateinit var statStreak: TextView
    private lateinit var statKept: TextView
    private lateinit var statLocks: TextView
    private lateinit var list: LinearLayout
    private var builtDark = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.setupWindow(this)
        builtDark = Ui.dark
        val c = this
        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL

        root.addView(Ui.link(c, "‹ Quay lại") { finish() })
        root.addView(Ui.text(c, "Nhật ký", 26f, Ui.TEXT, true), Ui.lp(c, 6))

        val stats = Ui.card(c)
        stats.orientation = LinearLayout.HORIZONTAL
        statStreak = addStat(stats, "ngày liên tiếp", false)
        statKept = addStat(stats, "ngày giữ luật", true)
        statLocks = addStat(stats, "lần khóa", true)
        root.addView(stats, Ui.lp(c, 14))

        val actions = LinearLayout(c)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.addView(Ui.link(c, "Chia sẻ / sao lưu (Notion…)") { shareLog() })
        actions.addView(Ui.link(c, "   Sao chép") { copyLog() })
        root.addView(actions, Ui.lp(c, 12))

        root.addView(
            Ui.text(c, "Chạm vào một lần khóa để ghi vì sao bạn khóa.", 13f, Ui.MUTED),
            Ui.lp(c, 10)
        )

        list = LinearLayout(c)
        list.orientation = LinearLayout.VERTICAL
        root.addView(list, Ui.lp(c, 6))

        setContentView(Ui.screen(c, root))
    }

    override fun onResume() {
        super.onResume()
        Ui.refreshMode(this)
        if (Ui.dark != builtDark) {
            recreate()
            return
        }
        render()
    }

    private fun shareLog() {
        try {
            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_SUBJECT, "Block - nhật ký")
            i.putExtra(Intent.EXTRA_TEXT, LockStore.exportText(this))
            startActivity(Intent.createChooser(i, "Gửi nhật ký tới"))
        } catch (_: Exception) {
            Toast.makeText(this, "Không mở được danh sách chia sẻ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyLog() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Block", LockStore.exportText(this)))
            Toast.makeText(this, "Đã sao chép nhật ký", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Không sao chép được", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addStat(parent: LinearLayout, label: String, withLeftMargin: Boolean): TextView {
        val c = this
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        val num = Ui.text(c, "0", 28f, Ui.TEXT, true)
        num.gravity = Gravity.CENTER
        col.addView(num)
        val lab = Ui.text(c, label, 12f, Ui.MUTED)
        lab.gravity = Gravity.CENTER
        col.addView(lab)
        val p = LinearLayout.LayoutParams(0, Ui.WRAP, 1f)
        if (withLeftMargin) p.leftMargin = Ui.dp(c, 8)
        parent.addView(col, p)
        return num
    }

    private fun render() {
        val log = LockStore.readLog(this)
        statStreak.text = LockStore.streak(this).toString()
        statKept.text = LockStore.keptTotal(this).toString()
        statLocks.text = log.size.toString()

        list.removeAllViews()
        if (log.isEmpty()) {
            list.addView(Ui.text(this, "Chưa có lần khóa nào.", 14f, Ui.MUTED))
            return
        }
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        for (i in log.indices) {
            val e = log[i]
            val card = Ui.card(this)
            card.addView(
                Ui.text(
                    this,
                    fmt.format(Date(e.time)) + "  ·  " + LockStore.label(e.minutes),
                    15f, Ui.TEXT, true
                )
            )
            if (e.apps.isNotBlank()) {
                card.addView(Ui.text(this, e.apps, 13f, Ui.MUTED), Ui.lp(this, 2))
            }
            val noteView = if (e.note.isBlank()) {
                Ui.text(this, "Chạm để ghi vì sao bạn khóa", 13f, Ui.LINK)
            } else {
                Ui.text(this, e.note, 14f, Ui.TEXT)
            }
            card.addView(noteView, Ui.lp(this, 6))
            card.setOnClickListener {
                Ui.prompt(
                    this, "Vì sao bạn khóa?", e.note,
                    "Ví dụ: vừa âm 2 lệnh liên tiếp, muốn gỡ lại ngay"
                ) { s ->
                    LockStore.setLogNote(this, i, s)
                    render()
                }
            }
            list.addView(card, Ui.lp(this, 10))
        }
    }
}
