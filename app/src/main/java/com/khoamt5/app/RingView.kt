package com.khoamt5.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Đồng hồ nước: một vòng tròn kính, bên trong nước có sóng.
 * - progress = 1 là đầy nước, hạ dần về 0 theo thời gian còn lại.
 * - Nghiêng điện thoại thì mặt nước nghiêng theo và sóng lắc.
 * - splash() cho nước dâng lên rồi chốt lại (dùng khi bấm KHÓA).
 */
class RingView(context: Context) : View(context) {

    var progress: Float = 0.8f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var ringColor: Int = Ui.TEAL
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var centerText: String = ""
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var subText: String = ""
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val glass = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG)
    private val waveBack = Paint(Paint.ANTI_ALIAS_FLAG)
    private val waveFront = Paint(Paint.ANTI_ALIAS_FLAG)
    private val big = Paint(Paint.ANTI_ALIAS_FLAG)
    private val small = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val wave = Path()

    // Trạng thái chuyển động
    private var tilt = 0f
    private var tiltTarget = 0f
    private var prevTilt = 0f
    private var impulse = 0f
    private var boost = 0f

    private var sensorManager: SensorManager? = null
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val ax = e.values[0].toDouble()
            val ay = e.values[1].toDouble()
            if (hypot(ax, ay) < 2.5) return   // điện thoại nằm phẳng: giữ nguyên
            val deg = Math.toDegrees(atan2(ax, ay)).toFloat()
            tiltTarget = deg.coerceIn(-55f, 55f)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    init {
        glass.style = Paint.Style.FILL
        glass.color = Color.argb(235, 255, 255, 255)
        outline.style = Paint.Style.STROKE
        waveBack.style = Paint.Style.FILL
        waveFront.style = Paint.Style.FILL
        big.color = Ui.TEXT
        big.textAlign = Paint.Align.CENTER
        big.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        small.color = Ui.TEXT
        small.textAlign = Paint.Align.CENTER
        small.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    /** Nước dâng lên rồi chốt lại, kèm sóng mạnh (dùng khi bấm KHÓA). */
    fun splash() {
        impulse = 1.4f
        boost = 0.16f
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        try {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val acc = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (sm != null && acc != null) {
                sm.registerListener(sensorListener, acc, SensorManager.SENSOR_DELAY_GAME)
                sensorManager = sm
            }
        } catch (_: Exception) {
        }
    }

    override fun onDetachedFromWindow() {
        try {
            sensorManager?.unregisterListener(sensorListener)
        } catch (_: Exception) {
        }
        sensorManager = null
        super.onDetachedFromWindow()
    }

    private fun withAlpha(c: Int, a: Int): Int =
        Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

    private fun buildWave(left: Float, right: Float, top: Float, bottom: Float,
                          amp: Float, lambda: Float, phase: Float) {
        wave.reset()
        wave.moveTo(left, bottom)
        var x = left
        while (x <= right) {
            val y = top + amp * sin(2.0 * PI * (x - left) / lambda + phase).toFloat()
            wave.lineTo(x, y)
            x += 8f
        }
        wave.lineTo(right, bottom)
        wave.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val stroke = minOf(w, h) * 0.022f
        val r = minOf(w, h) / 2f - stroke * 2f
        if (r <= 0f) return

        // Cập nhật chuyển động
        tilt += (tiltTarget - tilt) * 0.12f
        val vel = abs(tilt - prevTilt)
        prevTilt = tilt
        impulse *= 0.96f
        boost *= 0.94f
        val slosh = (1f + vel * 0.6f + impulse * 2.5f).coerceAtMost(4f)

        // Mặt kính
        glass.color = Ui.GLASS
        big.color = Ui.TEXT
        small.color = Ui.TEXT
        canvas.drawCircle(cx, cy, r, glass)

        // Mực nước
        val level = (0.05f + 0.9f * progress.coerceIn(0f, 1f) + boost).coerceAtMost(0.97f)
        val bottom = cy + r
        val top = bottom - level * 2f * r
        val t = SystemClock.uptimeMillis()
        val ph1 = (t % 2600L).toFloat() / 2600f * (2.0 * PI).toFloat()
        val ph2 = (t % 3900L).toFloat() / 3900f * (2.0 * PI).toFloat() + 1.6f
        val amp = r * 0.035f * slosh
        val lambda = r * 1.6f

        clip.reset()
        clip.addCircle(cx, cy, r, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)

        // Nước, nghiêng theo điện thoại. Vẽ rộng hơn vòng tròn để xoay không lộ góc trống.
        canvas.save()
        canvas.rotate(tilt, cx, cy)
        waveBack.color = withAlpha(ringColor, 80)
        buildWave(cx - 2f * r, cx + 2f * r, top - amp, cy + 2f * r, amp, lambda * 1.2f, ph2)
        canvas.drawPath(wave, waveBack)
        waveFront.color = withAlpha(ringColor, 130)
        buildWave(cx - 2f * r, cx + 2f * r, top, cy + 2f * r, amp, lambda, ph1)
        canvas.drawPath(wave, waveFront)
        canvas.restore()

        canvas.restore()

        // Viền
        outline.strokeWidth = stroke
        outline.color = ringColor
        canvas.drawCircle(cx, cy, r, outline)

        // Chữ
        val size = 2f * r
        big.textSize = if (centerText.length > 5) size * 0.17f else size * 0.22f
        val yBig = cy + big.textSize * 0.12f
        canvas.drawText(centerText, cx, yBig, big)
        small.textSize = size * 0.065f
        canvas.drawText(subText, cx, yBig + small.textSize * 2.0f, small)

        postInvalidateOnAnimation()
    }
}
