package com.khoamt5.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * "Chó canh": trong lúc khóa, luôn có một báo thức hẹn trước. Nếu hệ thống tắt tiến trình
 * của Block (vuốt dọn nền, trình dọn rác của hãng máy...) thì báo thức vẫn nổ và bật lại
 * dịch vụ giám sát. Dịch vụ còn sống thì cứ vài chục giây lại dời báo thức ra xa hơn.
 */
object Watchdog {
    const val ACTION = "com.khoamt5.app.WATCHDOG"
    private const val DEFAULT_DELAY_MS = 30_000L

    private fun pending(c: Context): PendingIntent {
        val app = c.applicationContext
        val i = Intent(app, BootReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(
            app, 7, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun schedule(c: Context, delayMs: Long = DEFAULT_DELAY_MS) {
        try {
            val am = c.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pending(c)
            )
        } catch (_: Exception) {
        }
    }

    fun cancel(c: Context) {
        try {
            val am = c.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pending(c))
        } catch (_: Exception) {
        }
    }
}
