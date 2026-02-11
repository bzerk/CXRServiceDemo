package com.example.cxrservicedemo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.Connection
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val pointPaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 8f
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val linePaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    @Volatile
    private var latestHands: List<List<NormalizedLandmark>> = emptyList()
    private var sourceWidth: Int = 1
    private var sourceHeight: Int = 1
    private var rotationDegrees: Int = 0

    fun update(result: HandLandmarkerResult, width: Int, height: Int, rotation: Int) {
        latestHands = result.landmarks()
        sourceWidth = width
        sourceHeight = height
        rotationDegrees = rotation
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val hands = latestHands
        if (hands.isEmpty() || sourceWidth == 0 || sourceHeight == 0) return

        val scaleX = width.toFloat()
        val scaleY = height.toFloat()

        hands.forEach { hand ->
            // Connections
            HandLandmarker.HAND_CONNECTIONS.forEach { connection ->
                drawConnection(canvas, hand, connection, scaleX, scaleY)
            }
            // Landmarks
            hand.forEach { lm ->
                val (nx, ny) = rotated(lm.x(), lm.y(), rotationDegrees)
                canvas.drawCircle(nx * scaleX, ny * scaleY, 6f, pointPaint)
            }
        }
    }

    private fun drawConnection(
        canvas: Canvas,
        hand: List<NormalizedLandmark>,
        connection: Connection,
        scaleX: Float,
        scaleY: Float
    ) {
        val start = hand.getOrNull(connection.start()) ?: return
        val end = hand.getOrNull(connection.end()) ?: return
        val (sx, sy) = rotated(start.x(), start.y(), rotationDegrees)
        val (ex, ey) = rotated(end.x(), end.y(), rotationDegrees)
        canvas.drawLine(
            sx * scaleX,
            sy * scaleY,
            ex * scaleX,
            ey * scaleY,
            linePaint
        )
    }

    private fun rotated(x: Float, y: Float, rotation: Int): Pair<Float, Float> {
        return when ((rotation % 360 + 360) % 360) {
            90 -> 1f - y to x
            180 -> 1f - x to 1f - y
            270 -> y to 1f - x
            else -> x to y
        }
    }
}
