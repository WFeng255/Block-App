package com.khoamt5.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Khởi động lại máy giữa lúc đang khóa thì bật lại dịch vụ giám sát. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && LockStore.isLocked(context)) {
            WatcherService.start(context)
        }
    }
}
