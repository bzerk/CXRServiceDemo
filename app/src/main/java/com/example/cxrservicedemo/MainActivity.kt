package com.example.cxrservicedemo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.cxrservicedemo.databinding.ActivityMainBinding
import com.rokid.cxr.CXRServiceBridge
import com.rokid.cxr.Caps
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val bridge = CXRServiceBridge()
    private val logLines = StringBuilder()
    private val messageCounter = AtomicInteger(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = "But_Why?"

        binding.subscribeButton.setOnClickListener { subscribe() }
        binding.sendButton.setOnClickListener { sendMessage() }
        binding.pairButton.setOnClickListener {
            bridge.startBTPairing()
            appendLog("Requested Bluetooth pairing.")
        }
        binding.disconnectButton.setOnClickListener {
            bridge.disconnectCXRDevice()
            appendLog("Requested disconnect.")
        }
        binding.clearLogButton.setOnClickListener {
            logLines.clear()
            appendLog("Log cleared.")
        }
        binding.handTrackingButton.setOnClickListener {
            startActivity(Intent(this, HandTrackingActivity::class.java))
        }

        bridge.setStatusListener(object : CXRServiceBridge.StatusListener {
            override fun onConnected(mac: String, name: String, type: Int) {
                updateStatus("Connected to $name", mac, type)
                appendLog("Connected: $mac, name=$name, type=$type")
            }

            override fun onDisconnected() {
                updateStatus("Disconnected", "--", CXRServiceBridge.StatusListener.DEVICE_TYPE_UNKNOWN)
                appendLog("Disconnected from device.")
            }

            override fun onConnecting(mac: String, name: String, type: Int) {
                updateStatus("Connecting to $name...", mac, type)
                appendLog("Connecting: $mac, name=$name, type=$type")
            }

            override fun onARTCStatus(health: Float, isTransmitOK: Boolean) {
                appendLog("ARTC status health=$health transmit=$isTransmitOK")
            }

            override fun onRokidAccountChanged(account: String) {
                appendLog("Account changed: $account")
            }
        })

        maybeRequestBluetoothPermissions()
        appendLog("CXR bridge ready. Tap Subscribe to listen on a topic, Send to push a message.")
    }

    private fun subscribe() {
        val topic = binding.topicInput.text.toString().trim()
        if (topic.isEmpty()) {
            appendLog("Topic is empty.")
            return
        }

        val result = bridge.subscribe(topic, object : CXRServiceBridge.MsgCallback {
            override fun onReceive(t: String, caps: Caps, data: ByteArray?) {
                runOnUiThread {
                    appendLog("Received on $t -> caps=$caps bytes=${data?.size ?: 0}")
                }
            }
        })

        appendLog("Subscribe($topic) -> ${describeResult(result)}")
    }

    private fun sendMessage() {
        val topic = binding.topicInput.text.toString().trim()
        val message = binding.messageInput.text.toString()
        if (topic.isEmpty()) {
            appendLog("Topic is empty.")
            return
        }

        val caps = Caps().apply {
            write("msg-${messageCounter.incrementAndGet()}")
            write(message)
        }

        val result = bridge.sendMessage(topic, caps)
        appendLog("Send($topic) -> ${describeResult(result)}")
    }

    private fun updateStatus(status: String, device: String, type: Int) {
        binding.statusText.text = "Status: $status"
        binding.deviceInfoText.text = "Device: $device (type=$type)"
    }

    private fun appendLog(line: String) {
        val timestamped = "${System.currentTimeMillis()}: $line"
        Log.d("CXRServiceDemo", line)
        logLines.appendLine(timestamped)
        binding.logText.text = logLines.toString()
    }

    private fun describeResult(code: Int): String = when (code) {
        0 -> "OK"
        CXRServiceBridge.EINVAL -> "EINVAL (bad args)"
        CXRServiceBridge.EDUP -> "EDUP (already subscribed)"
        CXRServiceBridge.EFAULT -> "EFAULT (service not ready?)"
        CXRServiceBridge.EBUSY -> "EBUSY (busy)"
        else -> "code=$code"
    }

    private fun maybeRequestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.BLUETOOTH_SCAN
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            permissions.zip(grantResults.toTypedArray()).forEach { (permission, result) ->
                appendLog("Permission $permission -> ${if (result == PackageManager.PERMISSION_GRANTED) "granted" else "denied"}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bridge.disconnectCXRDevice()
    }
}
