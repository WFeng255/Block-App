package com.khoamt5.app

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

/** Kiểm tra các quyền mà hai lớp chặn cần. */
object Perms {

    fun usage(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun overlay(c: Context): Boolean = Settings.canDrawOverlays(c)

    fun notif(c: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun battery(c: Context): Boolean {
        val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(c.packageName)
    }

    fun accessibility(c: Context): Boolean {
        val enabled = Settings.Secure.getString(
            c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val cn = ComponentName(c, LockService::class.java)
        val full = cn.flattenToString()
        val short = cn.flattenToShortString()
        return enabled.split(':').any { it.equals(full, true) || it.equals(short, true) }
    }

    /** Lớp 2 (giám sát nền) cần cả Usage access lẫn quyền hiện trên app khác. */
    fun layer2(c: Context): Boolean = usage(c) && overlay(c)
}
