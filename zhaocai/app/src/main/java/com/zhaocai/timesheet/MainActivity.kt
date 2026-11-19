package com.zhaocai.timesheet

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class Entry(val start: Long, val end: Long, val note: String) {
    val hours: Double get() = (end - start) / 3600000.0
}

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private val entries = mutableListOf<Entry>()
    private val ui = Handler(Looper.getMainLooper())
    private val red = Color.parseColor("#B71C1C")
    private val gold = Color.parseColor("#FFD54F")

    private lateinit var tvMonth: TextView
    private lateinit var tvMoney: TextView
    private lateinit var tvTimer: TextView
    private lateinit var btnClock: Button
    private lateinit var etNote: EditText
    private lateinit var list: LinearLayout

    private val dayFmt = SimpleDateFormat("MM/dd (E)", Locale.TAIWAN)
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.TAIWAN)

    private var running: Long
        get() = prefs.getLong("running", 0L)
        set(v) { prefs.edit().putLong("running", v).apply() }
    private var rate: Float
        get() = prefs.getFloat("rate", 200f)
        set(v) { prefs.edit().putFloat("rate", v).apply() }

    private val tick = object : Runnable {
        override fun run() {
            updateTimer()
            ui.postDelayed(this, 1000)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("zhaocai", MODE_PRIVATE)
        load()
        buildUi()
        refresh()
    }

    override fun onResume() { super.onResume(); ui.post(tick) }
    override fun onPause() { super.onPause(); ui.removeCallbacks(tick) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#FFF8E1")) }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(red)
            setPadding(dp(20), dp(36), dp(20), dp(20))
        }
        head.addView(TextView(this).apply { text = "招財工時簿"; setTextColor(gold); textSize = 14f })
        tvMonth = TextView(this).apply { setTextColor(Color.WHITE); textSize = 15f }
        tvMoney = TextView(this).apply { setTextColor(gold); textSize = 34f; typeface = Typeface.DEFAULT_BOLD }
        head.addView(tvMoney); head.addView(tvMonth)
        root.addView(head)

        val clock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(16), dp(16), dp(16), dp(8)) }
        tvTimer = TextView(this).apply { textSize = 40f; typeface = Typeface.MONOSPACE; setTextColor(Color.DKGRAY); gravity = Gravity.CENTER }
        etNote = EditText(this).apply { hint = "備註(選填)"; setSingleLine() }
        btnClock = Button(this).apply {
            setTextColor(Color.WHITE); textSize = 18f
            setOnClickListener { toggle() }
        }
        clock.addView(tvTimer); clock.addView(etNote, LinearLayout.LayoutParams(-1, -2))
        clock.addView(btnClock, LinearLayout.LayoutParams(-1, dp(56)))
        root.addView(clock)

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), 0, dp(12), 0) }
        fun small(t: String, a: () -> Unit) = Button(this).apply { text = t; setOnClickListener { a() } }
        bar.addView(small("＋補登") { addManual() }, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(small("時薪") { editRate() }, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(small("分享") { share() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(bar)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(24)) }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun toggle() {
        if (running == 0L) {
            running = System.currentTimeMillis()
        } else {
            val s = running; val e = System.currentTimeMillis()
            running = 0L
            if (e - s >= 1000) entries.add(Entry(s, e, etNote.text.toString().trim()))
            etNote.setText("")
            save()
        }
        refresh()
    }

    private fun addManual() {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            TimePickerDialog(this, { _, h1, m1 ->
                val s = Calendar.getInstance().apply { set(y, m, d, h1, m1, 0); set(Calendar.MILLISECOND, 0) }
                TimePickerDialog(this, { _, h2, m2 ->
                    val e = Calendar.getInstance().apply { set(y, m, d, h2, m2, 0); set(Calendar.MILLISECOND, 0) }
                    if (e.timeInMillis <= s.timeInMillis) e.add(Calendar.DAY_OF_MONTH, 1)
                    entries.add(Entry(s.timeInMillis, e.timeInMillis, etNote.text.toString().trim()))
                    etNote.setText(""); save(); refresh()
                }, h1 + 1, m1, true).apply { setTitle("下班時間") }.show()
            }, 9, 0, true).apply { setTitle("上班時間") }.show()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun editRate() {
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(rate.toString().removeSuffix(".0"))
        }
        AlertDialog.Builder(this).setTitle("每小時薪資").setView(et)
            .setPositiveButton("儲存") { _, _ ->
                et.text.toString().toFloatOrNull()?.let { rate = it; refresh() }
            }.setNegativeButton("取消", null).show()
    }

    private fun monthEntries(): List<Entry> {
        val now = Calendar.getInstance()
        return entries.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.start }
            c.get(Calendar.YEAR) == now.get(Calendar.YEAR) && c.get(Calendar.MONTH) == now.get(Calendar.MONTH)
        }
    }

    private fun share() {
        val sb = StringBuilder("招財工時簿\n")
        entries.sortedBy { it.start }.forEach {
            sb.append("${dayFmt.format(it.start)} ${timeFmt.format(it.end - (it.end - it.start))}-${timeFmt.format(it.end)}  %.2fh".format(it.hours))
            if (it.note.isNotEmpty()) sb.append("  ${it.note}")
            sb.append("\n")
        }
        val mh = monthEntries().sumOf { it.hours }
        sb.append("本月 %.2f 小時 / NT$%,.0f".format(mh, mh * rate))
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, sb.toString())
        }, "分享"))
    }

    private fun updateTimer() {
        val r = running
        if (r == 0L) { tvTimer.text = "00:00:00"; return }
        val s = (System.currentTimeMillis() - r) / 1000
        tvTimer.text = "%02d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
    }

    private fun refresh() {
        val on = running != 0L
        btnClock.text = if (on) "下班打卡" else "上班打卡"
        btnClock.setBackgroundColor(if (on) Color.parseColor("#455A64") else red)
        val mh = monthEntries().sumOf { it.hours }
        tvMoney.text = "NT$ %,.0f".format(mh * rate)
        tvMonth.text = "本月 %.2f 小時 ・ 時薪 %s".format(mh, rate.toString().removeSuffix(".0"))
        updateTimer()

        list.removeAllViews()
        if (entries.isEmpty()) {
            list.addView(TextView(this).apply { text = "還沒有紀錄,按「上班打卡」開始!"; setPadding(0, dp(24), 0, 0); gravity = Gravity.CENTER })
        }
        entries.sortedByDescending { it.start }.forEach { e ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setOnLongClickListener {
                    AlertDialog.Builder(this@MainActivity).setMessage("刪除這筆紀錄?")
                        .setPositiveButton("刪除") { _, _ -> entries.remove(e); save(); refresh() }
                        .setNegativeButton("取消", null).show()
                    true
                }
            }
            row.addView(TextView(this).apply {
                text = "${dayFmt.format(e.start)}  ${timeFmt.format(e.start)}–${timeFmt.format(e.end)}"
                textSize = 15f; setTextColor(Color.BLACK)
            })
            row.addView(TextView(this).apply {
                text = "%.2f 小時 ・ NT$%,.0f".format(e.hours, e.hours * rate) + if (e.note.isNotEmpty()) "\n${e.note}" else ""
                setTextColor(Color.GRAY)
            })
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
    }

    private fun save() {
        val a = JSONArray()
        entries.forEach { a.put(JSONObject().put("s", it.start).put("e", it.end).put("n", it.note)) }
        prefs.edit().putString("entries", a.toString()).apply()
    }

    private fun load() {
        entries.clear()
        val a = JSONArray(prefs.getString("entries", "[]"))
        for (i in 0 until a.length()) a.getJSONObject(i).let { entries.add(Entry(it.getLong("s"), it.getLong("e"), it.optString("n"))) }
    }
}
