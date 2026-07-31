package com.arbounce.playground.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.random.Random

/**
 * Dependency-free 2D "tilt & bounce" sandbox used when ARCore is unavailable so the
 * app is always playable. Gravity follows the device tilt (accelerometer); tap to
 * fling the ball. Emits throw/bounce callbacks that feed the same lifetime stats.
 */
class FallbackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs), SensorEventListener {

    var onThrow: (() -> Unit)? = null
    var onBounce: ((Float) -> Unit)? = null

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var px = 0f
    private var py = 0f
    private var vx = 0f
    private var vy = 0f
    private val radius = 46f
    private var gx = 0f
    private var gy = 2200f // default downward gravity (px/s^2) if no sensor

    private var lastNs = 0L

    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 0, 0, 0)
    }

    init {
        px = 300f; py = 300f
    }

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        lastNs = 0L
        postInvalidateOnAnimation()
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        px = w / 2f
        py = h / 3f
        bgPaint.shader = RadialGradient(
            w / 2f, h / 2f, maxOf(w, h).toFloat(),
            intArrayOf(Color.parseColor("#1B3A4B"), Color.parseColor("#0A1418")),
            null, Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        step()

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        // shadow on the floor
        val floorY = height - radius
        val shrink = ((floorY - py) / height).coerceIn(0f, 1f)
        canvas.drawOval(
            px - radius * (1.1f - 0.3f * shrink), floorY + 8,
            px + radius * (1.1f - 0.3f * shrink), floorY + 8 + radius * 0.5f,
            shadowPaint,
        )
        // ball
        ballPaint.shader = RadialGradient(
            px - radius * 0.3f, py - radius * 0.3f, radius * 1.6f,
            intArrayOf(Color.parseColor("#FFB199"), Color.parseColor("#FF7043")),
            null, Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(px, py, radius, ballPaint)

        postInvalidateOnAnimation()
    }

    private fun step() {
        val now = System.nanoTime()
        val dt = if (lastNs == 0L) 0f else ((now - lastNs) / 1_000_000_000f).coerceAtMost(0.05f)
        lastNs = now
        if (dt <= 0f) return

        vx += gx * dt
        vy += gy * dt
        px += vx * dt
        py += vy * dt

        val e = 0.75f
        var bounced = false
        var impact = 0f
        if (px < radius) { px = radius; if (vx < 0) { impact = abs(vx); vx = -vx * e; bounced = true } }
        if (px > width - radius) { px = width - radius; if (vx > 0) { impact = abs(vx); vx = -vx * e; bounced = true } }
        if (py < radius) { py = radius; if (vy < 0) { impact = abs(vy); vy = -vy * e; bounced = true } }
        if (py > height - radius) {
            py = height - radius
            if (vy > 0) { impact = abs(vy); vy = -vy * e; bounced = true }
            vx *= 0.98f // floor friction
        }
        if (bounced && impact > 220f) onBounce?.invoke(impact / 1000f)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            // fling toward the tap point with an upward kick
            val dx = event.x - px
            val dy = event.y - py
            vx = dx * 3f + Random.nextInt(-200, 200)
            vy = dy * 3f - 1600f
            onThrow?.invoke()
            performClick()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Portrait: tilting right pushes ball right; tilting forward pulls it "down".
        val ax = event.values[0]
        val ay = event.values[1]
        gx = -ax * 220f
        gy = ay * 220f + 900f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
