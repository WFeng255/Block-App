package com.khoamt5.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * Lớp chặn 1 (Trợ năng): nghe sự kiện đổi cửa sổ. Mỗi lần một app trong danh sách khóa
 * hiện lên (kể cả mở lại sau khi thoát) thì hiện ngay màn hình che (cửa sổ loại trợ năng,
 * không cần thêm quyền), giữ vài giây cho bạn đọc lời nhắc rồi đẩy về màn hình chính.
 * Không đọc nội dung màn hình (canRetrieveWindowContent = false).
 */
class LockService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentPkg: String? = null
    private var looping = false
    private var lastHome = 0L
    private val protectedPkgs by lazy { LockStore.protectedPackages(this) }
    private val imePkgs by lazy { loadImePackages() }

    private fun loadImePackages(): Set<String> {
        return try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.enabledInputMethodList.map { it.packageName }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun isBlocked(pkg: String): Boolean =
        pkg in LockStore.lockedPackages(this) && pkg !in protectedPkgs

    private fun home() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastHome < 800L) return
        lastHome = now
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private val loop = object : Runnable {
        override fun run() {
            val pkg = currentPkg
            val rem = LockStore.remainingMs(this@LockService)
            if (pkg == null || rem <= 0L || !isBlocked(pkg)) {
                looping = false
                if (BlockOverlay.owner == BlockOverlay.OWNER_ACC) BlockOverlay.hide()
                return
            }
            val ok = BlockOverlay.show(
                this@LockService,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                rem,
                BlockOverlay.OWNER_ACC
            ) { home() }
            if (!ok) home()
            handler.postDelayed(this, 300L)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Bỏ qua thanh hệ thống và bàn phím: chúng không phải app người dùng đang ở.
        if (pkg == "com.android.systemui" || pkg in imePkgs) return

        // Cửa sổ che của chính mình cũng báo sự kiện: bỏ qua, chỉ tính các màn hình (Activity) của app.
        if (pkg == packageName) {
            val cn = event.className?.toString() ?: ""
            if (!cn.endsWith("Activity")) return
        }

        currentPkg = pkg
        if (pkg != packageName) LockStore.accSeen = pkg

        if (isBlocked(pkg) && LockStore.remainingMs(this) > 0L) {
            if (!looping) {
                looping = true
                handler.post(loop)
            }
        } else if (BlockOverlay.owner == BlockOverlay.OWNER_ACC) {
            BlockOverlay.hide()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(loop)
        looping = false
        if (BlockOverlay.owner == BlockOverlay.OWNER_ACC) BlockOverlay.hide()
        super.onDestroy()
    }
}
