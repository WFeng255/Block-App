package com.khoamt5.app

import android.content.Context
import android.content.Intent
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Một lần khóa trong nhật ký. */
data class LogEntry(val time: Long, val minutes: Long, val apps: String, val note: String)

/**
 * Lưu thời điểm hết khóa, danh sách app bị khóa, lời nhắc, nhật ký và chuỗi ngày kỷ luật
 * vào SharedPreferences nên vẫn còn khi tắt app, vuốt app khỏi đa nhiệm hoặc khởi động lại máy.
 */
object LockStore {
    const val MT5_PACKAGE = "net.metaquotes.metatrader5"

    /** Giá trị đặc biệt: khóa tới hết ngày (0 giờ sáng hôm sau). */
    const val END_OF_DAY = -1L

    /** Các mức thời gian cho mục "Khác" (phút): 1 phút (để thử) ... 24 giờ. */
    val STEPS_MIN = longArrayOf(1, 5, 15, 30, 60, 120, 240, 480, 720, 1440)

    const val DEFAULT_REMINDER =
        "Hít thở sâu. Một lệnh thua không phải là cả ngày. Làm đúng luật mới là thắng."

    private const val PREFS = "lock"
    private const val KEY_STARTED = "started"
    private const val KEY_UNTIL = "until"
    private const val KEY_TOTAL = "total"
    private const val KEY_SEL_MIN = "sel_min"
    private const val KEY_PACKAGES = "packages"
    private const val KEY_REMINDER = "reminder"
    private const val KEY_LOG = "log"
    private const val KEY_DAYS = "days"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_COLOR = "color_mode"

    // Thông tin chẩn đoán: mỗi lớp chặn vừa "thấy" app nào mở.
    @Volatile var accSeen: String = ""
    @Volatile var watchSeen: String = ""
    @Volatile var watchAliveAt: Long = 0L

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- Định dạng thời gian ----------

    fun label(minutes: Long): String = when {
        minutes < 60L -> "$minutes phút"
        minutes % 60L == 0L -> "${minutes / 60L} giờ"
        else -> "${minutes / 60L} giờ ${minutes % 60L} phút"
    }

    fun formatMs(ms: Long): String {
        val s = (ms + 999L) / 1000L
        val h = s / 3600L
        val m = (s % 3600L) / 60L
        val sec = s % 60L
        return if (h > 0L) String.format("%d:%02d:%02d", h, m, sec)
        else String.format("%02d:%02d", m, sec)
    }

    // ---------- Thời gian khóa đã chọn ----------

    fun selectedMinutes(c: Context): Long = prefs(c).getLong(KEY_SEL_MIN, 15L)

    fun setSelectedMinutes(c: Context, minutes: Long) {
        prefs(c).edit().putLong(KEY_SEL_MIN, minutes).apply()
    }

    fun minutesUntilMidnight(): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance()
        next.add(Calendar.DAY_OF_YEAR, 1)
        next.set(Calendar.HOUR_OF_DAY, 0)
        next.set(Calendar.MINUTE, 0)
        next.set(Calendar.SECOND, 0)
        next.set(Calendar.MILLISECOND, 0)
        val ms = next.timeInMillis - now.timeInMillis
        return ((ms + 59_999L) / 60_000L).coerceAtLeast(1L)
    }

    /** Đổi giá trị đã chọn (có thể là END_OF_DAY) thành số phút thật. */
    fun resolveMinutes(selected: Long): Long =
        if (selected == END_OF_DAY) minutesUntilMidnight() else selected

    // ---------- Danh sách app ----------

    /** Danh sách app sẽ bị khóa. Mặc định chỉ có MetaTrader 5. */
    fun lockedPackages(c: Context): Set<String> =
        prefs(c).getStringSet(KEY_PACKAGES, null)?.toSet() ?: setOf(MT5_PACKAGE)

    /** Đổi danh sách app. Đang khóa thì không cho đổi. */
    fun setLockedPackages(c: Context, packages: Set<String>) {
        if (remainingMs(c) > 0L) return
        prefs(c).edit().putStringSet(KEY_PACKAGES, HashSet(packages)).apply()
    }

    /**
     * App không bao giờ bị khóa, để khỏi mất đường gọi khẩn cấp
     * hoặc kẹt không thoát được ra màn hình chính / cài đặt.
     */
    fun protectedPackages(c: Context): Set<String> {
        val pm = c.packageManager
        val result = mutableSetOf(
            c.packageName,
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.emergency",
            "com.android.server.telecom",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.incallui",
            "com.samsung.android.incallui",
            "com.android.contacts",
            "com.google.android.contacts"
        )
        fun addAll(intent: Intent) {
            try {
                pm.queryIntentActivities(intent, 0).forEach { result.add(it.activityInfo.packageName) }
            } catch (_: Exception) {
            }
        }
        addAll(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        addAll(Intent(Intent.ACTION_DIAL))
        addAll(Intent(Settings.ACTION_SETTINGS))
        return result
    }

    // ---------- Giao diện ----------

    /** 0 = tự động theo hệ thống, 1 = sáng (mặc định), 2 = tối. */
    fun themeMode(c: Context): Int = prefs(c).getInt(KEY_THEME, 1)

    fun setThemeMode(c: Context, mode: Int) {
        prefs(c).edit().putInt(KEY_THEME, mode).apply()
    }

    /** 0 = đổi màu theo trạng thái, 1 = chuyển màu từ từ. */
    fun colorMode(c: Context): Int = prefs(c).getInt(KEY_COLOR, 0)

    fun setColorMode(c: Context, mode: Int) {
        prefs(c).edit().putInt(KEY_COLOR, mode).apply()
    }

    // ---------- Lời nhắc ----------

    fun reminder(c: Context): String {
        val s = prefs(c).getString(KEY_REMINDER, null)
        return if (s == null || s.isBlank()) DEFAULT_REMINDER else s
    }

    fun setReminder(c: Context, text: String) {
        prefs(c).edit().putString(KEY_REMINDER, text.trim()).apply()
    }

    // ---------- Khóa ----------

    fun isLocked(c: Context): Boolean = remainingMs(c) > 0L

    /** Bắt đầu khóa. Đang khóa rồi thì không làm gì (không thể kéo dài hay rút ngắn). */
    fun start(c: Context, minutes: Long) {
        if (remainingMs(c) > 0) return
        val now = System.currentTimeMillis()
        prefs(c).edit()
            .putLong(KEY_STARTED, now)
            .putLong(KEY_UNTIL, now + minutes * 60_000L)
            .putLong(KEY_TOTAL, minutes * 60_000L)
            .commit()
    }

    fun totalMs(c: Context): Long = prefs(c).getLong(KEY_TOTAL, 0L)

    /** Số mili-giây còn lại, 0 nếu không khóa. */
    fun remainingMs(c: Context): Long {
        val p = prefs(c)
        val until = p.getLong(KEY_UNTIL, 0L)
        if (until == 0L) return 0L
        val started = p.getLong(KEY_STARTED, 0L)
        val now = System.currentTimeMillis()
        // Chỉnh đồng hồ lùi về trước lúc bắt đầu thì không được lợi: coi như vừa bắt đầu.
        val effectiveNow = if (now < started) started else now
        return (until - effectiveNow).coerceAtLeast(0L)
    }

    // ---------- Nhật ký các lần khóa ----------

    fun addLog(c: Context, minutes: Long, apps: String) {
        val old = try {
            JSONArray(prefs(c).getString(KEY_LOG, "[]"))
        } catch (_: Exception) {
            JSONArray()
        }
        val entry = JSONObject()
        entry.put("t", System.currentTimeMillis())
        entry.put("m", minutes)
        entry.put("apps", apps)
        entry.put("note", "")
        val out = JSONArray()
        out.put(entry)
        var i = 0
        while (i < old.length() && out.length() < 100) {
            out.put(old.get(i))
            i++
        }
        prefs(c).edit().putString(KEY_LOG, out.toString()).apply()
    }

    fun readLog(c: Context): List<LogEntry> {
        val arr = try {
            JSONArray(prefs(c).getString(KEY_LOG, "[]"))
        } catch (_: Exception) {
            return emptyList()
        }
        val out = ArrayList<LogEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(LogEntry(o.optLong("t"), o.optLong("m"), o.optString("apps"), o.optString("note")))
        }
        return out
    }

    fun setLogNote(c: Context, index: Int, note: String) {
        val arr = try {
            JSONArray(prefs(c).getString(KEY_LOG, "[]"))
        } catch (_: Exception) {
            return
        }
        val o = arr.optJSONObject(index) ?: return
        o.put("note", note.trim())
        prefs(c).edit().putString(KEY_LOG, arr.toString()).apply()
    }

    // ---------- Chuỗi ngày giữ kỷ luật ----------

    fun dayKey(offsetDays: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, offsetDays)
        return String.format(
            Locale.US, "%04d-%02d-%02d",
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun readDays(c: Context): JSONObject = try {
        JSONObject(prefs(c).getString(KEY_DAYS, "{}"))
    } catch (_: Exception) {
        JSONObject()
    }

    fun markToday(c: Context, kept: Boolean) {
        val o = readDays(c)
        o.put(dayKey(0), kept)
        prefs(c).edit().putString(KEY_DAYS, o.toString()).apply()
    }

    /** true = giữ đúng luật, false = đã phá luật, null = hôm nay chưa ghi. */
    fun todayMark(c: Context): Boolean? {
        val o = readDays(c)
        val k = dayKey(0)
        return if (o.has(k)) o.optBoolean(k) else null
    }

    /** Ghi nhận của một ngày (offset 0 = hôm nay, -1 = hôm qua...). */
    fun dayMark(c: Context, offset: Int): Boolean? {
        val o = readDays(c)
        val k = dayKey(offset)
        return if (o.has(k)) o.optBoolean(k) else null
    }

    fun dayOfMonth(offset: Int): Int {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, offset)
        return cal.get(Calendar.DAY_OF_MONTH)
    }

    /** Số ngày liên tiếp giữ đúng luật (hôm nay chưa ghi thì tính từ hôm qua). */
    fun streak(c: Context): Int {
        val o = readDays(c)
        var offset = if (o.has(dayKey(0))) 0 else -1
        var n = 0
        while (n < 3650) {
            val k = dayKey(offset)
            if (o.has(k) && o.optBoolean(k)) {
                n++
                offset--
            } else {
                break
            }
        }
        return n
    }

    /** Toàn bộ nhật ký và chuỗi ngày dưới dạng văn bản, để chia sẻ hoặc sao lưu. */
    fun exportText(c: Context): String {
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val log = readLog(c)
        val sb = StringBuilder()
        sb.append("Block - nhật ký\n")
        sb.append("Xuất lúc ").append(fmt.format(Date())).append("\n")
        sb.append("Chuỗi hiện tại: ").append(streak(c)).append(" ngày liên tiếp · ")
        sb.append(keptTotal(c)).append(" ngày giữ luật · ")
        sb.append(log.size).append(" lần khóa\n\n")
        sb.append("CÁC LẦN KHÓA\n")
        if (log.isEmpty()) sb.append("(chưa có)\n")
        for (e in log) {
            sb.append("- ").append(fmt.format(Date(e.time))).append(" · ").append(label(e.minutes))
            if (e.apps.isNotBlank()) sb.append(" · ").append(e.apps)
            sb.append("\n")
            if (e.note.isNotBlank()) sb.append("  Ghi chú: ").append(e.note).append("\n")
        }
        sb.append("\nNGÀY GIỮ LUẬT\n")
        val o = readDays(c)
        val keys = ArrayList<String>()
        val it = o.keys()
        while (it.hasNext()) keys.add(it.next())
        keys.sortDescending()
        if (keys.isEmpty()) sb.append("(chưa có)\n")
        for (k in keys) {
            sb.append(k).append(": ").append(if (o.optBoolean(k)) "giữ đúng luật" else "đã phá luật").append("\n")
        }
        return sb.toString()
    }

    fun keptTotal(c: Context): Int {
        val o = readDays(c)
        var n = 0
        val keys = o.keys()
        while (keys.hasNext()) {
            if (o.optBoolean(keys.next())) n++
        }
        return n
    }
}
