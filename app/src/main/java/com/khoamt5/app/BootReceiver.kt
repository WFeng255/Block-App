package com.khoamt5.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Bật lại dịch vụ giám sát khi đang khóa mà bị tắt: sau khi khởi động lại máy,
 * hoặc khi báo thức của Watchdog nổ (tiến trình bị hệ thống tắt giữa chừng).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val a = intent?.action
        if (a != Intent.ACTION_BOOT_COMPLETED && a != Watchdog.ACTION) return
        if (LockStore.isLocked(context)) {
            WatcherService.start(context)
            Watchdog.schedule(context)
        } else {
            Watchdog.cancel(context)
        }
    }
}
