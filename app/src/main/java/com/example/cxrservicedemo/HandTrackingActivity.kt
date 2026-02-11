package com.example.cxrservicedemo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.SystemClock
import android.util.Range
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.camera2.interop.Camera2Interop
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.cxrservicedemo.databinding.ActivityHandTrackingBinding
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.nio.ByteBuffer
import android.util.Size
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class HandTrackingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHandTrackingBinding
    private var handLandmarker: HandLandmarker? = null
    private lateinit var cameraExecutor: ExecutorService
    private var currentNumHands = 2

    private val cameraPermission = Manifest.permission.CAMERA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHandTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.statusText.setTextColor(Color.GREEN)

        binding.previewView.setBackgroundColor(Color.BLACK)
        binding.overlay.setBackgroundColor(Color.BLACK)
        binding.overlay.setOnClickListener {
            toggleNumHands()
        }
        binding.statusText.setOnClickListener {
            toggleNumHands()
        }

        if (hasCameraPermission()) {
            setupHandLandmarker()
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handLandmarker?.close()
        cameraExecutor.shutdown()
    }

    private fun setupHandLandmarker() {
        handLandmarker?.close()
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .setDelegate(Delegate.GPU) // Prefer Adreno GPU; falls back to CPU if unavailable
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(currentNumHands)
            .setResultListener { result, inputImage ->
                runOnUiThread {
                    binding.statusText.text = "Hands: ${result.landmarks().size} (mode $currentNumHands)"
                    binding.overlay.update(
                        result,
                        inputImage.width,
                        inputImage.height,
                        latestRotationDegrees
                    )
                }
            }
            .build()

        handLandmarker = HandLandmarker.createFromOptions(this, options)
        binding.statusText.text = "Hand tracker ready (mode $currentNumHands)"
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = androidx.camera.core.Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            val analysisBuilder = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)

            // Try full 30 FPS capture; GPU delegate should cope.
            Camera2Interop.Extender(analysisBuilder).setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range(30, 30)
            )

            val analysis = analysisBuilder.build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                runHandTracking(imageProxy)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (exc: Exception) {
                binding.statusText.text = "Camera error: ${exc.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleNumHands() {
        currentNumHands = if (currentNumHands == 2) 1 else 2
        setupHandLandmarker()
    }

    private fun runHandTracking(imageProxy: ImageProxy) {
        val landmarker = handLandmarker
        if (landmarker == null) {
            imageProxy.close()
            return
        }

        // Throttle to ~30 FPS (matching capture request).
        val now = SystemClock.uptimeMillis()
        if (now - lastAnalyzeTimestampMs < 34) {
            imageProxy.close()
            return
        }
        lastAnalyzeTimestampMs = now

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        latestRotationDegrees = rotationDegrees

        val bitmap = imageProxy.toBitmap() ?: run {
            imageProxy.close()
            return
        }

        val mpImage = BitmapImageBuilder(bitmap).build()
        val imageOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(rotationDegrees)
            .build()

        val timestampMs = SystemClock.uptimeMillis()
        try {
            landmarker.detectAsync(mpImage, imageOptions, timestampMs)
        } catch (e: Exception) {
            runOnUiThread {
                binding.statusText.text = "Hand tracker error: ${e.message}"
            }
        } finally {
            imageProxy.close()
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, cameraPermission) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(cameraPermission), REQUEST_CAMERA)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupHandLandmarker()
                startCamera()
            } else {
                binding.statusText.text = "Camera permission denied"
            }
        }
    }

    private fun ImageProxy.toBitmap(): Bitmap? {
        if (format != PixelFormat.RGBA_8888) {
            return null
        }
        val plane = planes.firstOrNull() ?: return null
        val buffer: ByteBuffer = plane.buffer
        val width = width
        val height = height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        buffer.rewind()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (pixelStride == 4 && rowStride == width * 4) {
            bitmap.copyPixelsFromBuffer(buffer)
        } else {
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            val packed = ByteBuffer.allocate(width * height * 4)
            for (y in 0 until height) {
                val rowStart = y * rowStride
                packed.put(data, rowStart, width * pixelStride)
            }
            packed.rewind()
            bitmap.copyPixelsFromBuffer(packed)
        }
        return bitmap
    }

    companion object {
        private const val REQUEST_CAMERA = 200
    }

    private var lastAnalyzeTimestampMs: Long = 0L
    @Volatile private var latestRotationDegrees: Int = 0
}
