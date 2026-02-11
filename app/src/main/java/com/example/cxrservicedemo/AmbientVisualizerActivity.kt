package com.example.cxrservicedemo

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.cxrservicedemo.databinding.ActivityAmbientVisualizerBinding
import kotlin.math.max
import kotlin.math.min

class AmbientVisualizerActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityAmbientVisualizerBinding
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null
    private var audioRecord: AudioRecord? = null
    private var audioThread: Thread? = null
    @Volatile private var running = false
    @Volatile private var baseYaw: Float? = null
    @Volatile private var basePitch: Float? = null
    @Volatile private var baseRoll: Float? = null
    @Volatile private var latestAmp: Float = 0f
    private var sensorLabel: String = ""
    private var useFusionFallback = false
    private var runningPeak = 400.0
    private val gravity = FloatArray(3)
    private var hasGravity = false
    private var yawFromFusion = 0f
    private var pitchFromFusion = 0f
    private var rollFromFusion = 0f
    private var lastGyroTimestampNs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAmbientVisualizerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "But_Why?"
        supportActionBar?.hide()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationSensor = chooseRotationSensor()
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        useFusionFallback = rotationSensor == null && accelSensor != null && gyroSensor != null
        binding.visualizer.setBackgroundColor(android.graphics.Color.BLACK)
        binding.visualizer.setStatusText("Ambient visualizer ready")
        binding.visualizer.setOnClickListener {
            binding.visualizer.toggleStatus()
        }

        startSensors()
        ensureAudioPermissionThenStart()
    }

    override fun onResume() {
        super.onResume()
        startSensors()
        if (running) startAudio()
    }

    override fun onPause() {
        super.onPause()
        stopSensors()
        stopAudio()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSensors()
        stopAudio()
    }

    private fun startSensors() {
        rotationSensor = chooseRotationSensor()
        useFusionFallback = rotationSensor == null && accelSensor != null && gyroSensor != null
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            binding.visualizer.setStatusText("Using sensor: $sensorLabel")
        } ?: run {
            if (useFusionFallback) {
                sensorLabel = "GYRO+ACC"
                accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
                gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
                binding.visualizer.setStatusText("Using sensor: $sensorLabel")
            } else {
                binding.visualizer.setStatusText("No rotation sensor available")
            }
        }
    }

    private fun stopSensors() {
        sensorManager.unregisterListener(this)
    }

    private fun ensureAudioPermissionThenStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            startAudio()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
        }
    }

    private fun startAudio() {
        stopAudio()
        running = true
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(minBuf, 2048)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.UNPROCESSED,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        audioRecord?.startRecording()

        audioThread = Thread {
            val buf = ShortArray(bufferSize)
            while (running && !Thread.currentThread().isInterrupted) {
                val read = audioRecord?.read(buf, 0, buf.size) ?: break
                if (read > 0) {
                    var sum = 0.0
                    for (i in 0 until read) {
                        val v = buf[i].toInt()
                        sum += v * v
                    }
                    val rms = kotlin.math.sqrt(sum / read)
                    // Adaptive normalization: track running peak and scale to ~0..1
                    runningPeak = kotlin.math.max(rms, runningPeak * 0.99 + rms * 0.01)
                    val norm = (rms / (runningPeak * 0.6 + 1.0)).coerceIn(0.0, 1.0)
                    latestAmp = norm.toFloat()
                    pushState()
                }
            }
        }.also { it.start() }
    }

    private fun stopAudio() {
        running = false
        audioThread?.interrupt()
        audioThread = null
        audioRecord?.run {
            try { stop() } catch (_: Exception) {}
            try { release() } catch (_: Exception) {}
        }
        audioRecord = null
    }

    private fun pushState() {
        val yaw = currentYaw
        val pitch = currentPitch
        val roll = currentRoll
        if (yaw != null && pitch != null && roll != null) {
            val by = baseYaw ?: yaw
            val bp = basePitch ?: pitch
            val br = baseRoll ?: roll
            baseYaw = by
            basePitch = bp
            baseRoll = br

            val dy = yaw - by
            val dp = pitch - bp
            val dr = roll - br
            binding.visualizer.update(latestAmp, dy, dp, dr)
            binding.visualizer.setStatusText(
                "${if (sensorLabel.isNotEmpty()) "$sensorLabel " else ""}" +
                        "amp=${"%.2f".format(latestAmp)} " +
                        "yaw=${fmtDeg(dy)} pitch=${fmtDeg(dp)} roll=${fmtDeg(dr)}"
            )
        } else {
            binding.visualizer.update(latestAmp, 0f, 0f, 0f)
            binding.visualizer.setStatusText("waiting for sensors…")
        }
    }

    private fun fmtDeg(rad: Float): String {
        val deg = Math.toDegrees(rad.toDouble())
        return "%.1f°".format(deg)
    }

    private fun chooseRotationSensor(): Sensor? {
        val primary = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (primary != null) {
            sensorLabel = "ROT"
            return primary
        }
        val game = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        if (game != null) {
            sensorLabel = "GAME_ROT"
            return game
        }
        val geomag = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        if (geomag != null) {
            sensorLabel = "GEO_ROT"
            return geomag
        }
        sensorLabel = ""
        return null
    }

    private fun fuseAndPush() {
        if (!hasGravity) return
        val accelPitch = -kotlin.math.atan2(
            gravity[0].toDouble(),
            kotlin.math.sqrt((gravity[1] * gravity[1] + gravity[2] * gravity[2]).toDouble())
        ).toFloat()
        val accelRoll = kotlin.math.atan2(gravity[1].toDouble(), gravity[2].toDouble()).toFloat()
        val alpha = 0.98f
        pitchFromFusion = alpha * pitchFromFusion + (1 - alpha) * accelPitch
        rollFromFusion = alpha * rollFromFusion + (1 - alpha) * accelRoll

        if (baseYaw == null) {
            baseYaw = yawFromFusion
            basePitch = pitchFromFusion
            baseRoll = rollFromFusion
        }
        currentYaw = yawFromFusion
        currentPitch = pitchFromFusion
        currentRoll = rollFromFusion
        pushState()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startAudio()
        } else {
            binding.visualizer.setStatusText("Audio permission denied")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_GAME_ROTATION_VECTOR,
            Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                val rotVec = event.values.clone()
                val rotMat = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotMat, rotVec)
            // Remap to better suit glasses landscape orientation: keep X, map Y<-Z.
            val remapped = FloatArray(9)
            SensorManager.remapCoordinateSystem(
                rotMat,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                remapped
            )
            val orient = FloatArray(3)
            SensorManager.getOrientation(remapped, orient)
            val yaw = orient[0]
            val pitch = -orient[1]
            val roll = orient[2]
            if (baseYaw == null) {
                baseYaw = yaw
                basePitch = pitch
                baseRoll = roll
                }
                currentYaw = yaw
                currentPitch = pitch
                currentRoll = roll
                pushState()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (!useFusionFallback) return
                val alpha = 0.9f
                gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
                gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
                gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]
                hasGravity = true
                fuseAndPush()
            }
            Sensor.TYPE_GYROSCOPE -> {
                if (!useFusionFallback) return
                val ts = event.timestamp
                if (lastGyroTimestampNs != 0L) {
                    val dt = (ts - lastGyroTimestampNs) / 1_000_000_000f
                    yawFromFusion += event.values[2] * dt
                    pitchFromFusion += event.values[0] * dt
                    rollFromFusion += event.values[1] * dt
                }
                lastGyroTimestampNs = ts
                fuseAndPush()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // no-op
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val REQ_AUDIO = 301
    }

    @Volatile private var currentYaw: Float? = null
    @Volatile private var currentPitch: Float? = null
    @Volatile private var currentRoll: Float? = null
}
