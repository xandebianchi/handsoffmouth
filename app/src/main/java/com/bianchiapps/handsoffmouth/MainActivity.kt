package com.bianchiapps.handsoffmouth

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var handLandmarker: HandLandmarker? = null
    private var faceLandmarker: FaceLandmarker? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isCameraPermissionGranted = false

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        isCameraPermissionGranted = isGranted
        if (isGranted) {
            initializeApp()
        } else {
            Toast.makeText(this, getString(R.string.str_camera_permission_needed), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            isCameraPermissionGranted = true
            initializeApp()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onStart() {
        super.onStart()
        if (isCameraPermissionGranted) {
            initializeHandLandmarker()
            initializeFaceLandmarker()
        }
    }

    override fun onStop() {
        super.onStop()
        handLandmarker?.close()
        faceLandmarker?.close()
        handLandmarker = null
        faceLandmarker = null
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun initializeApp() {
        // Initialize Camera Runner and Notification Sound
        cameraExecutor = Executors.newSingleThreadExecutor()
        mediaPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)

        // Initialize the hand and face detectors
        initializeHandLandmarker()
        initializeFaceLandmarker()

        // Configure the interface using Compose
        setContent {
            // State variables for Compose
            var isHandOnMouth by remember { mutableStateOf(false) }

            LaunchedEffect(isCameraPermissionGranted) {
                if (isCameraPermissionGranted) {
                    startCamera { detectedHandsOnMouth ->
                        if (detectedHandsOnMouth && !isHandOnMouth) {
                            isHandOnMouth = true
                            playNotificationSound()
                        } else if (!detectedHandsOnMouth && isHandOnMouth) {
                            isHandOnMouth = false
                        }
                    }
                }
            }

            DetectionUI(
                isHandsOnMouth = isHandOnMouth,
            )
        }
    }

    private fun initializeHandLandmarker() {
        if (handLandmarker == null) {
            handLandmarker = HandLandmarker.createFromFile(this, "hand_landmarker.task")
        }
    }

    private fun initializeFaceLandmarker() {
        if (faceLandmarker == null) {
            faceLandmarker = FaceLandmarker.createFromFile(this, "face_landmarker.task")
        }
    }

    private fun startCamera(updateStatus: (Boolean) -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy, updateStatus)
                        imageProxy.close()
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalyzer)
            } catch (e: Exception) {
                Log.e("CameraX", getString(R.string.str_error_starting_camera), e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImage(imageProxy: ImageProxy, updateStatus: (Boolean) -> Unit) {
        val bitmap = imageProxyToBitmap(imageProxy)
        val mpImage = BitmapImageBuilder(bitmap).build()

        val handResult = handLandmarker?.detect(mpImage)
        val faceResult = faceLandmarker?.detect(mpImage)

        if (handResult?.landmarks()?.isNotEmpty() == true &&
            faceResult?.faceLandmarks()?.isNotEmpty() == true
        ) {
            checkFingerOnMouth(handResult, faceResult, updateStatus)
        } else {
            updateStatus(false)
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height),
            100,
            out
        )
        val imageBytes = out.toByteArray()
        var bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        // Fix image rotation
        val matrix = Matrix()
        matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        return bitmap
    }

    private fun checkFingerOnMouth(
        handResult: HandLandmarkerResult,
        faceResult: FaceLandmarkerResult,
        updateStatus: (Boolean) -> Unit
    ) {
        // Center of the mouth (using landmarks 13 and 14 for the upper and lower lips)
        val mouthCenterX =
            (faceResult.faceLandmarks()[0][13].x() + faceResult.faceLandmarks()[0][14].x()) / 2
        val mouthCenterY =
            (faceResult.faceLandmarks()[0][13].y() + faceResult.faceLandmarks()[0][14].y()) / 2

        // Check each detected hand
        for (hand in handResult.landmarks()) {
            // Five fingers to check
            val fingerPoints = listOf(hand[4], hand[8], hand[12], hand[16], hand[20])

            for (finger in fingerPoints) {
                val distance = calculateDistance(finger.x(), finger.y(), mouthCenterX, mouthCenterY)

                if (distance < 0.1f) {
                    updateStatus(true)
                    return
                }
            }
        }

        updateStatus(false)
    }

    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
    }

    private fun playNotificationSound() {
        mediaPlayer?.start()
    }
}

@Composable
fun DetectionUI(isHandsOnMouth: Boolean) {
    val animatedColor by animateColorAsState(
        targetValue = if (isHandsOnMouth) Color.Red else Color.Green,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing), label = ""
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        if (isHandsOnMouth) {
            Text(
                text = LocalContext.current.getString(R.string.str_hands_off_mouth),
                fontSize = 78.sp,
                color = animatedColor,
                style = TextStyle(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            Text(
                text = LocalContext.current.getString(R.string.str_monitoring),
                fontSize = 24.sp,
                color = animatedColor,
                style = TextStyle(fontWeight = FontWeight.Thin),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
