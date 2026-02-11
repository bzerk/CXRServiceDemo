package com.example.cxrservicedemo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.SystemClock
import android.util.Range
import android.util.Size
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.camera.camera2.interop.Camera2Interop
import com.example.cxrservicedemo.databinding.ActivityObjectLabelsBinding
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.nio.ByteBuffer

class ObjectLabelsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityObjectLabelsBinding
    private var detector: ObjectDetector? = null
    private lateinit var cameraExecutor: ExecutorService
    private var latestRotationDegrees: Int = 0
    private var lastAnalyzeTimestampMs: Long = 0

    private val cameraPermission = Manifest.permission.CAMERA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityObjectLabelsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        cameraExecutor = Executors.newSingleThreadExecutor()
        binding.previewView.setBackgroundColor(Color.BLACK)
        binding.overlay.setBackgroundColor(Color.BLACK)

        if (hasCameraPermission()) {
            setupDetector()
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector?.close()
        cameraExecutor.shutdown()
    }

    private fun setupDetector() {
        detector?.close()
        // For stability, run on CPU (GPU delegate caused invalid argument on device).
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("efficientdet-lite0.tflite")
            .setDelegate(Delegate.CPU)
            .build()

        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(baseOptions)
            .setMaxResults(5)
            .build()

        detector = ObjectDetector.createFromOptions(this, options)
        binding.overlay.setStatus("Detector ready (CPU)")
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = androidx.camera.core.Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }

            val analysisBuilder = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)

            Camera2Interop.Extender(analysisBuilder).setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range(15, 15)
            )

            val analysis = analysisBuilder.build()
            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                runDetection(imageProxy)
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
                binding.overlay.setStatus("Camera error: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun runDetection(imageProxy: ImageProxy) {
        val det = detector ?: run { imageProxy.close(); return }

        val now = SystemClock.uptimeMillis()
        if (now - lastAnalyzeTimestampMs < 120) {
            imageProxy.close()
            return
        }
        lastAnalyzeTimestampMs = now

        latestRotationDegrees = imageProxy.imageInfo.rotationDegrees

        val bitmap = imageProxy.toBitmap() ?: run {
            imageProxy.close(); return
        }

        val mpImage = BitmapImageBuilder(bitmap).build()
        val imageOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(latestRotationDegrees)
            .build()

        try {
            val result = det.detect(mpImage, imageOptions)
            runOnUiThread {
                binding.overlay.update(result, bitmap.width, bitmap.height, latestRotationDegrees, now)
                binding.overlay.setStatus("Detections: ${result.detections().size}")
            }
        } catch (e: Exception) {
            runOnUiThread { binding.overlay.setStatus("Detect error: ${e.message}") }
        } finally {
            imageProxy.close()
        }
    }

    private fun ImageProxy.toBitmap(): android.graphics.Bitmap? {
        if (format != android.graphics.PixelFormat.RGBA_8888) return null
        val plane = planes.firstOrNull() ?: return null
        val buffer: ByteBuffer = plane.buffer
        val width = width
        val height = height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        buffer.rewind()
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
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
                setupDetector()
                startCamera()
            } else {
                binding.overlay.setStatus("Camera permission denied")
            }
        }
    }

    companion object {
        private const val REQUEST_CAMERA = 301
    }
}
