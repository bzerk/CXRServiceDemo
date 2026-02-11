package com.example.cxrservicedemo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class AmbientVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    private val fillPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        isAntiAlias = true
        alpha = 60
    }
    private val textPaint = Paint().apply {
        color = Color.GREEN
        textSize = 28f
        isAntiAlias = true
    }

    @Volatile private var amplitude: Float = 0f
    @Volatile private var yaw: Float = 0f
    @Volatile private var pitch: Float = 0f
    @Volatile private var roll: Float = 0f
    @Volatile private var status: String = ""
    private var lastFrameTime: Long = 0L
    private var lastYawRaw: Float? = null
    private var yawAccum: Float = 0f
    private var biasYaw: Float = 0f
    private var lastPitchRaw: Float? = null
    private var pitchAccum: Float = 0f
    private var biasPitch: Float = 0f
    private val particles: MutableList<Particle> = mutableListOf()
    @Volatile private var showStatus: Boolean = true

    fun update(amp: Float, yaw: Float, pitch: Float, roll: Float) {
        amplitude = amp
        // Unwrap yaw to avoid -pi..pi jumps
        lastYawRaw = if (lastYawRaw == null) {
            yaw
        } else {
            val prev = lastYawRaw!!
            var delta = yaw - prev
            if (delta > Math.PI) delta -= (2 * Math.PI).toFloat()
            if (delta < -Math.PI) delta += (2 * Math.PI).toFloat()
            yawAccum += delta
            yaw
        }
        lastPitchRaw = if (lastPitchRaw == null) {
            pitch
        } else {
            val prev = lastPitchRaw!!
            var delta = pitch - prev
            if (delta > Math.PI) delta -= (2 * Math.PI).toFloat()
            if (delta < -Math.PI) delta += (2 * Math.PI).toFloat()
            pitchAccum += delta
            pitch
        }
        this.yaw = yawAccum
        this.pitch = pitchAccum
        this.roll = roll
        postInvalidateOnAnimation()
    }

    fun setStatusText(text: String) {
        status = text
        postInvalidateOnAnimation()
    }

    fun toggleStatus() {
        showStatus = !showStatus
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val screenW = width.toFloat()
        val screenH = height.toFloat()
        canvas.drawColor(Color.BLACK)

        val t = System.nanoTime() / 1_000_000_000.0
        val amp = amplitude.coerceIn(0f, 1f)

        val nowNs = System.nanoTime()
        val dt = if (lastFrameTime == 0L) 0f else (nowNs - lastFrameTime) / 1_000_000_000f
        val maxDelta = (5.0 * Math.PI / 180.0 * dt).toFloat() // 5 deg/sec
        fun ease(current: Float, target: Float): Float {
            val delta = target - current
            val clamped = delta.coerceIn(-maxDelta, maxDelta)
            return current + clamped
        }
        biasYaw = ease(biasYaw, yaw)
        biasPitch = ease(biasPitch, pitch)
        val displayYaw = yaw - biasYaw
        val displayPitch = pitch - biasPitch

        // Create a world larger than the screen; pan via yaw/pitch.
        val worldScale = 5f
        val worldW = screenW * worldScale
        val worldH = screenH * worldScale
        val worldCenterX = worldW / 2f
        val worldCenterY = worldH / 2f

        // Map yaw/pitch to camera offsets in the world.
        val camXRaw = worldCenterX + displayYaw * worldW * 0.25f
        val camYRaw = worldCenterY - displayPitch * worldH * 0.25f
        val camX = camXRaw.coerceIn(screenW / 2f, worldW - screenW / 2f)
        val camY = camYRaw.coerceIn(screenH / 2f, worldH - screenH / 2f)

        fun worldToScreen(wx: Float, wy: Float): Pair<Float, Float> {
            return Pair(wx - camX + screenW / 2f, wy - camY + screenH / 2f)
        }

        val baseRadius = min(worldW, worldH) * 0.09f

        // Pulsing rings
        val rings = 4
        for (i in 0 until rings) {
            val phase = (t * 0.6 + i * 0.35)
            val r = baseRadius * (1f + i * 0.55f) * (1f + amp * 0.6f * sin(phase).toFloat())
            paint.alpha = (50 + 70 * cos(phase)).toInt().coerceIn(10, 180)
            paint.strokeWidth = 2f + amp * 4f
            val (sx, sy) = worldToScreen(worldCenterX, worldCenterY)
            canvas.drawCircle(sx, sy, r, paint)
        }

        // Ripple wave
        if (amp > 0.05f) {
            val ripplePhase = (t * 1.4) % 1.0
            val rippleR = baseRadius * (0.8f + 2.4f * ripplePhase.toFloat())
            paint.alpha = (60 + 160 * (1 - ripplePhase)).toInt().coerceIn(5, 220)
            paint.strokeWidth = 1.5f + amp * 4f
            val (rcx, rcy) = worldToScreen(worldCenterX, worldCenterY)
            canvas.drawCircle(rcx, rcy, rippleR, paint)
        }

        // Particles around (stable per launch)
        if (particles.isEmpty()) {
            val count = 32
            repeat(count) {
                particles += Particle(
                    angleOffset = Random.nextDouble(0.0, 2 * Math.PI),
                    radiusFactor = 0.9f + Random.nextDouble(-0.1, 0.1).toFloat(),
                    hollow = Random.nextBoolean()
                )
            }
        }
        val particlesCount = particles.size
        val swirl = t * 1.0
        val maxR = baseRadius * (2.2f + amp * 1.6f)
        particles.forEachIndexed { i, p ->
            val angle = swirl + p.angleOffset
            val radius = maxR * p.radiusFactor * (0.7 + 0.5 * sin(t * 0.7 + i)).toFloat()
            val wx = worldCenterX + radius * cos(angle).toFloat()
            val wy = worldCenterY + radius * sin(angle).toFloat()
            val (sx, sy) = worldToScreen(wx, wy)
            val size = (6f + amp * 12f) * (0.9f + Random.nextFloat() * 0.2f)
            if (p.hollow) {
                paint.strokeWidth = 2f + amp * 2f
                paint.alpha = 180
                canvas.drawCircle(sx, sy, size, paint)
            } else {
                canvas.drawCircle(sx, sy, size, fillPaint)
            }
        }

        // Status text
        if (showStatus && status.isNotEmpty()) {
            canvas.drawText(status, 24f, screenH - 24f, textPaint)
        }

        lastFrameTime = nowNs
    }

    private data class Particle(
        val angleOffset: Double,
        val radiusFactor: Float,
        val hollow: Boolean
    )
}
