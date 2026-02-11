package com.example.cxrservicedemo

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AppCompatActivity
import com.example.cxrservicedemo.databinding.ActivityDemoSelectorBinding

class DemoSelectorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDemoSelectorBinding
    private lateinit var buttons: List<android.widget.Button>
    private var selectedIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDemoSelectorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        buttons = listOf(binding.btnAmbient, binding.btnHandTracking, binding.btnBluetooth, binding.btnObjectLabels)
        updateSelection()

        binding.btnAmbient.setOnClickListener {
            startActivity(Intent(this, AmbientVisualizerActivity::class.java))
        }
        binding.btnHandTracking.setOnClickListener {
            startActivity(Intent(this, HandTrackingActivity::class.java))
        }
        binding.btnBluetooth.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        binding.btnObjectLabels.setOnClickListener {
            startActivity(Intent(this, ObjectLabelsActivity::class.java))
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                moveSelection(1)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                moveSelection(-1)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                launchSelected()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun moveSelection(delta: Int) {
        selectedIndex = (selectedIndex + delta + buttons.size) % buttons.size
        updateSelection()
    }

    private fun launchSelected() {
        when (selectedIndex) {
            0 -> startActivity(Intent(this, AmbientVisualizerActivity::class.java))
            1 -> startActivity(Intent(this, HandTrackingActivity::class.java))
            2 -> startActivity(Intent(this, MainActivity::class.java))
            3 -> startActivity(Intent(this, ObjectLabelsActivity::class.java))
        }
    }

    private fun updateSelection() {
        buttons.forEachIndexed { index, button ->
            val drawable = GradientDrawable().apply {
                setColor(Color.argb(255, 0, 0, 0))
                if (index == selectedIndex) {
                    setStroke(6, Color.GREEN)
                    setColor(Color.argb(60, 0, 255, 0))
                    button.elevation = 8f
                    button.alpha = 1f
                } else {
                    setStroke(0, Color.TRANSPARENT)
                    button.elevation = 0f
                    button.alpha = 0.6f
                }
                cornerRadius = 12f
            }
            button.background = drawable
            button.invalidate()
        }
    }
}
