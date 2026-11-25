package com.khoamt5.app

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.View
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager

/**
 * Lớp chặn 2: dịch vụ chạy nền (có thông báo cố định nên khó bị hệ thống tắt).
 * Cứ 0,3 giây đọc xem app nào đang ở trên cùng (qua Usage access). Nếu đó là app bị khóa
 * thì hiện màn hình che (BlockOverlay) rồi đẩy về màn hình chính.
 * Chỉ chạy trong lúc đang khóa, hết giờ tự tắt.
 */
class WatcherService : Service() {

    companion object {
        private const val CHANNEL_ID = "khoa_app_watch"
        private const val NOTIF_ID = 1001
        private const val EV_FG = 1          // app lên trước
        private const val EV_BG = 2          // app xuống nền
        private const val EV_SCREEN_OFF = 16 // tắt màn hình
        private const val POLL_MS = 300L
        private const val SHIELD_MS = 400L  // thời gian khóa cảm ứng sau khi rời app bị khóa

        fun start(c: Context) {
            try {
                c.startForegroundService(Intent(c, WatcherService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var fgPackage: String? = null
    // App bị khóa đã báo "vào" mà chưa báo "rời"/"dừng". Màn hình chính hay đa nhiệm báo "vào" thì
    // chưa chắc app kia đã đi (thoát đa nhiệm là quay lại thẳng app, không có sự kiện "vào" mới).
    private val frontLocked = HashSet<String>()
    private var homePkgs: Set<String> = emptySet()
    private var heldSince = 0L
    private var lastEventTs = 0L
    private var lastHome = 0L
    private var lastDog = 0L
    private val seenEv = HashSet<String>()
    private var lastKill = 0L
    private var burstUntil = 0L
    private var lastFgForKill: String? = null
    private var firstRefresh = true
    private var lastKillLog = 0L
    private var shield: View? = null
    private var shieldUntil = 0L
    private var protectedPkgs: Set<String> = emptySet()
    private var running = false

    private val poll = object : Runnable {
        override fun run() {
            if (!running) return
            val remaining = LockStore.remainingMs(this@WatcherService)
            if (remaining <= 0L) {
                running = false
                Watchdog.cancel(this@WatcherService)
                BlockOverlay.hide()
                removeShield()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            LockStore.watchAliveAt = System.currentTimeMillis()
            val tNow = SystemClock.elapsedRealtime()
            if (tNow - lastDog > 15_000L) {
                lastDog = tNow
                Watchdog.schedule(this@WatcherService)
            }
            refreshForeground()

            // Tắt tiến trình nền của app bị khóa để lần mở sau là khởi động mới, luôn có sự kiện "vào".
            val lockedNow = LockStore.lockedPackages(this@WatcherService)
            var front = frontLocked.firstOrNull { it in lockedNow && it !in protectedPkgs }
            // Chốt an toàn: nếu sự kiện "rời" không bao giờ tới thì không giữ màn che mãi, tránh kẹt máy.
            if (front != null && front != fgPackage) {
                if (heldSince == 0L) heldSince = tNow
                if (tNow - heldSince > 12_000L) {
                    frontLocked.clear()
                    heldSince = 0L
                    front = null
                    LockStore.diag("L2", "thả giữ: quá 12s không thấy rời")
                }
            } else {
                heldSince = 0L
            }
            val cur = front ?: fgPackage
            val prev = lastFgForKill
            if (prev != null && prev != cur && prev in lockedNow && prev !in protectedPkgs) {
                burstUntil = tNow + 5000L
                shieldUntil = tNow + SHIELD_MS
                lastKill = 0L // tắt ngay trong lượt này
            }
            lastFgForKill = cur
            updateShield(tNow, cur != null && cur in lockedNow && cur !in protectedPkgs)
            val gap = if (tNow < burstUntil) 250L else 1500L
            if (tNow - lastKill > gap) {
                lastKill = tNow
                killLockedInBackground(tNow)
            }

            val pkg = front ?: fgPackage
            if (pkg != null &&
                pkg in LockStore.lockedPackages(this@WatcherService) &&
                pkg !in protectedPkgs
            ) {
                val fresh = BlockOverlay.owner == BlockOverlay.OWNER_NONE
                val ok = BlockOverlay.show(
                    this@WatcherService,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    remaining,
                    BlockOverlay.OWNER_WATCH
                ) { goHome() }
                if (fresh) LockStore.diag(
                    "L2",
                    "che " + pkg + (if (ok) " ok" else " LỖI") +
                        (if (pkg != fgPackage) " [giữ: chưa thấy rời]" else "")
                )
                if (!ok) goHome()
            } else if (BlockOverlay.owner == BlockOverlay.OWNER_WATCH) {
                BlockOverlay.hide()
            }
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        protectedPkgs = LockStore.protectedPackages(this)
        homePkgs = try {
            packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
            ).map { it.activityInfo.packageName }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (!LockStore.isLocked(this)) {
            running = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) {
            running = true
            lastEventTs = System.currentTimeMillis() - 2L * 60L * 60L * 1000L
            handler.post(poll)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Bị vuốt khỏi đa nhiệm: hẹn báo thức để bật lại ngay nếu hệ thống tắt luôn tiến trình. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (LockStore.isLocked(this)) Watchdog.schedule(this, 1500L)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        removeShield()
        if (BlockOverlay.owner == BlockOverlay.OWNER_WATCH) BlockOverlay.hide()
        super.onDestroy()
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Block", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Block đang chạy")
            .setContentText("Đang giám sát các app bị khóa")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    /** Cập nhật app đang ở trên cùng dựa trên sự kiện sử dụng mới nhất. */
    private fun refreshForeground() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        try {
            // Đọc lùi thêm 4 giây và xử lý lại theo thứ tự: nếu hệ thống ghi sự kiện trễ hoặc lệch giờ
            // (hay gặp khi quay lại app từ màn hình đa nhiệm) thì vẫn không bỏ sót.
            val events = usm.queryEvents((lastEventTs - 4000L).coerceAtLeast(0L), System.currentTimeMillis())
            val e = UsageEvents.Event()
            var maxTs = lastEventTs
            val quiet = firstRefresh
            val locked = LockStore.lockedPackages(this)
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.timeStamp > maxTs) maxTs = e.timeStamp
                val pk = e.packageName
                // Ghi lại mọi sự kiện (trừ đổi nhóm chờ) kèm giờ của chính sự kiện, mỗi sự kiện một lần.
                val noise = e.eventType == 4 || e.eventType == 10 || e.eventType == 12
                if (!noise && seenEv.add(e.timeStamp.toString() + "|" + e.eventType + "|" + pk) && !quiet) {
                    val what = when (e.eventType) {
                        EV_FG -> "vào"
                        EV_BG -> "rời"
                        23 -> "dừng"
                        7 -> "chạm"
                        else -> "t" + e.eventType
                    }
                    LockStore.diag("L2", what + " " + (if (pk == packageName) "Block" else pk) + " @" + (e.timeStamp % 100000L))
                }
                when (e.eventType) {
                    EV_FG -> {
                        fgPackage = pk
                        if (pk in locked && pk !in protectedPkgs) {
                            frontLocked.clear()
                            frontLocked.add(pk)
                        } else if (pk !in homePkgs && pk != "com.android.systemui") {
                            // một app thật khác lên trước: app bị khóa chắc chắn đã đi
                            frontLocked.clear()
                        }
                    }
                    EV_BG -> {
                        if (pk == fgPackage) fgPackage = null
                        frontLocked.remove(pk)
                    }
                    23 -> frontLocked.remove(pk)
                    EV_SCREEN_OFF -> {
                        fgPackage = null
                        frontLocked.clear()
                    }
                }
            }
            firstRefresh = false
            if (seenEv.size > 400) seenEv.clear()
            lastEventTs = maxTs
        } catch (_: Exception) {
        }
        val p = fgPackage
        if (p != null && p != packageName && p != "com.android.systemui") {
            LockStore.watchSeen = p
        }
    }

    private fun killLockedInBackground(nowMs: Long) {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val fg = fgPackage
        val tried = ArrayList<String>()
        for (p in LockStore.lockedPackages(this)) {
            if (p == fg || p == packageName || p in protectedPkgs) continue
            try {
                am.killBackgroundProcesses(p)
                tried.add(p)
            } catch (_: Exception) {
            }
        }
        if (tried.isNotEmpty() && nowMs - lastKillLog > 10_000L) {
            lastKillLog = nowMs
            LockStore.diag("L2", "tắt nền " + tried.size + " app: " + tried.joinToString(", "))
        }
    }

    /**
     * Sau khi rời một app bị khóa, phủ một lớp trong suốt nuốt hết thao tác chạm trong ít giây,
     * để không kịp bấm lại vào app đó từ màn hình đa nhiệm trước khi nó bị tắt nền.
     * Nếu app bị khóa đã quay lại phía trước thì để màn che lo, bỏ lớp này.
     */
    private fun updateShield(nowMs: Long, lockedInFront: Boolean) {
        val want = nowMs < shieldUntil && !lockedInFront
        if (want && shield == null) {
            if (!Settings.canDrawOverlays(this)) return
            val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
            val v = View(this)
            v.setOnTouchListener { _, _ -> true }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            try {
                wm.addView(v, lp)
                shield = v
                LockStore.diag("L2", "khóa chạm " + SHIELD_MS + "ms")
            } catch (_: Exception) {
            }
        } else if (!want && shield != null) {
            removeShield()
            if (lockedInFront) LockStore.diag("L2", "bỏ khóa chạm: app khóa đã quay lại")
        }
    }

    private fun removeShield() {
        val v = shield ?: return
        shield = null
        try {
            (getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.removeView(v)
        } catch (_: Exception) {
        }
    }

    private fun goHome() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastHome < 800L) return
        lastHome = now
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }
}
