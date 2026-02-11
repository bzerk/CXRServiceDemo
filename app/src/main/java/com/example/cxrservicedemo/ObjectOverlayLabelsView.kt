package com.example.cxrservicedemo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult

class ObjectOverlayLabelsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val textPaint = Paint().apply {
        color = Color.GREEN
        textSize = 26f
        isAntiAlias = true
    }

    private data class Slot(
        var label: String? = null,
        var pinyin: String? = null,
        var lastSeen: Long = 0L
    )

    private val slots = MutableList(MAX_SLOTS) { Slot() }
    @Volatile private var status: String = ""

    fun update(
        result: ObjectDetectorResult,
        srcWidth: Int,
        srcHeight: Int,
        rotationDegrees: Int,
        timestampMs: Long = SystemClock.uptimeMillis()
    ) {
        val now = timestampMs

        // Helper: find existing label slot
        fun findSlotFor(label: String): Int? {
            return slots.indexOfFirst { it.label == label }.takeIf { it >= 0 }
        }

        // Assign detections to slots without shuffling existing ones
        result.detections().forEach { detection ->
            val cat = detection.categories().maxByOrNull { it.score() } ?: return@forEach
            val label = cat.categoryName()
            val pinyin = labelToPinyin[label.lowercase()] ?: ""
            val existingIdx = findSlotFor(label)
            if (existingIdx != null) {
                val slot = slots[existingIdx]
                slot.lastSeen = now
                slot.pinyin = pinyin
            } else {
                val emptyIdx = slots.indexOfFirst { it.label == null }
                if (emptyIdx >= 0) {
                    slots[emptyIdx].label = label
                    slots[emptyIdx].pinyin = pinyin
                    slots[emptyIdx].lastSeen = now
                }
            }
        }

        // Expire stale slots (>5s)
        val cutoff = now - 5000
        slots.forEach { slot ->
            if (slot.label != null && slot.lastSeen < cutoff) {
                slot.label = null
                slot.pinyin = null
                slot.lastSeen = 0L
            }
        }

        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(Color.BLACK)

        val lineHeight = textPaint.textSize * 1.2f
        val startY = textPaint.textSize * 1.4f
        val startX = textPaint.textSize * 0.6f

        slots.forEachIndexed { idx, slot ->
            val y = startY + idx * lineHeight
            if (slot.label != null) {
                val label = slot.label ?: ""
                val pinyin = slot.pinyin?.takeIf { it.isNotEmpty() }?.let { " / $it" } ?: ""
                val line = label + pinyin
                canvas.drawText(line, startX, y, textPaint)
            } else {
                // empty slot: optionally draw nothing (keeps vertical space stable)
            }
        }

        if (status.isNotEmpty()) {
            canvas.drawText(status, 16f, h - 24f, textPaint)
        }
    }

    fun setStatus(text: String) {
        status = text
        postInvalidateOnAnimation()
    }

    companion object {
        private const val MAX_SLOTS = 6
    }
}
