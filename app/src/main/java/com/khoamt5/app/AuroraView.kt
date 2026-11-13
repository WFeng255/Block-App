package com.khoamt5.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Nền aurora: vài mảng màu mờ trôi chậm trên nền gần trắng (hoặc gần đen khi nền tối).
 * Hai cách đổi màu:
 *  - Theo trạng thái: sẵn sàng là xanh ngọc, tím, xanh trời; đang khóa là cam, hồng, tím nhạt.
 *  - Chuyển màu từ từ: sắc độ xoay chậm qua cả dải màu, khoảng 1 phút một vòng.
 */
class AuroraView(context: Context) : View(context) {

    var locked: Boolean = false
        set(value) {
            field = value
            target = if (value) 1f else 0f
            invalidate()
        }

    private var target = 0f
    private var mix = 0f
    private var shaderMix = -1f
    private var shaderRadius = 0f
    private var shaderDark = false
    private var shaderCycle = false
    private var shaderKey = -1L

    private val calm = intArrayOf(
        Color.parseColor("#1FD1B8"),
        Color.parseColor("#8B5CF6"),
        Color.parseColor("#38BDF8"),
        Color.parseColor("#4ADE80")
    )
    private val hot = intArrayOf(
        Color.parseColor("#FF6B4A"),
        Color.parseColor("#F472B6"),
        Color.parseColor("#FB923C"),
        Color.parseColor("#A78BFA")
    )

    // Vị trí gốc (tỉ lệ theo chiều rộng/cao) và chu kỳ trôi của từng mảng.
    private val bx = floatArrayOf(0.15f, 0.95f, 0.10f, 0.85f)
    private val by = floatArrayOf(0.08f, 0.28f, 0.68f, 0.92f)
    private val period = floatArrayOf(11000f, 14000f, 17000f, 13000f)

    private val base = Paint()
    private val paints = Array(4) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val hsv = FloatArray(3)

    private fun lerp(a: Int, b: Int, t: Float): Int {
        val r = Color.red(a) + ((Color.red(b) - Color.red(a)) * t).toInt()
        val g = Color.green(a) + ((Color.green(b) - Color.green(a)) * t).toInt()
        val bl = Color.blue(a) + ((Color.blue(b) - Color.blue(a)) * t).toInt()
        return Color.rgb(r, g, bl)
    }

    private fun blobColor(i: Int, timeMs: Long): Int {
        if (Ui.cycle) {
            // Xoay sắc độ: 60 giây một vòng, mỗi mảng lệch nhau 85 độ.
            hsv[0] = ((timeMs % 60000L).toFloat() / 60000f * 360f + i * 85f) % 360f
            hsv[1] = if (Ui.dark) 0.75f else 0.60f
            hsv[2] = 1f
            return Color.HSVToColor(hsv)
        }
        return lerp(calm[i], hot[i], mix)
    }

    private fun rebuildShaders(radius: Float, timeMs: Long) {
        val alpha = if (Ui.dark) 150 else 225
        for (i in 0 until 4) {
            val c = blobColor(i, timeMs)
            val center = Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c))
            val edge = Color.argb(0, Color.red(c), Color.green(c), Color.blue(c))
            paints[i].shader = RadialGradient(0f, 0f, radius, center, edge, Shader.TileMode.CLAMP)
        }
        shaderMix = mix
        shaderRadius = radius
        shaderDark = Ui.dark
        shaderCycle = Ui.cycle
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Chuyển màu mượt giữa hai trạng thái
        if (mix != target) {
            val step = 0.04f
            mix = if (mix < target) minOf(target, mix + step) else maxOf(target, mix - step)
        }
        val now = SystemClock.uptimeMillis()
        val radius = maxOf(w, h) * 0.62f
        val key = if (Ui.cycle) now / 120L else 0L
        if (shaderMix != mix || shaderRadius != radius || shaderDark != Ui.dark ||
            shaderCycle != Ui.cycle || shaderKey != key
        ) {
            rebuildShaders(radius, now)
            shaderKey = key
        }

        base.color = Ui.PAGE_END
        canvas.drawRect(0f, 0f, w, h, base)

        val t = now.toFloat()
        for (i in 0 until 4) {
            val ang = t / period[i] * 6.2831853f + i * 1.7f
            val x = w * bx[i] + w * 0.10f * sin(ang)
            val y = h * by[i] + h * 0.05f * cos(ang * 0.8f)
            canvas.save()
            canvas.translate(x, y)
            canvas.drawCircle(0f, 0f, radius, paints[i])
            canvas.restore()
        }
        postInvalidateOnAnimation()
    }
}
