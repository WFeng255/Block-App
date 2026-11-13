package com.khoamt5.app

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

        fun start(c: Context) {
            try {
                c.startForegroundService(Intent(c, WatcherService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var fgPackage: String? = null
    private var lastEventTs = 0L
    private var lastHome = 0L
    private var protectedPkgs: Set<String> = emptySet()
    private var running = false

    private val poll = object : Runnable {
        override fun run() {
            if (!running) return
            val remaining = LockStore.remainingMs(this@WatcherService)
            if (remaining <= 0L) {
                running = false
                BlockOverlay.hide()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            LockStore.watchAliveAt = System.currentTimeMillis()
            refreshForeground()

            val pkg = fgPackage
            if (pkg != null &&
                pkg in LockStore.lockedPackages(this@WatcherService) &&
                pkg !in protectedPkgs
            ) {
                val ok = BlockOverlay.show(
                    this@WatcherService,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    remaining,
                    BlockOverlay.OWNER_WATCH
                ) { goHome() }
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

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
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
            val events = usm.queryEvents(lastEventTs, System.currentTimeMillis())
            val e = UsageEvents.Event()
            var maxTs = lastEventTs
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.timeStamp > maxTs) maxTs = e.timeStamp
                when (e.eventType) {
                    EV_FG -> fgPackage = e.packageName
                    EV_BG -> if (e.packageName == fgPackage) fgPackage = null
                    EV_SCREEN_OFF -> fgPackage = null
                }
            }
            lastEventTs = maxTs
        } catch (_: Exception) {
        }
        val p = fgPackage
        if (p != null && p != packageName && p != "com.android.systemui") {
            LockStore.watchSeen = p
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
