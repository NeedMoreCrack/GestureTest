package com.example.gesturetest

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

import com.example.gesturetest.camera.CameraController
import com.example.gesturetest.gesture.GestureStabilizer
import com.example.gesturetest.gesture.MediaPipeHandDetector
import com.example.gesturetest.ui.CameraScreen
import com.example.gesturetest.ui.theme.GestureTestTheme

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"

        /*
         * 每 2 個 Camera frame
         * 執行一次 HaGRID。
         */
        private const val HAGRID_FRAME_INTERVAL = 2

        /*
         * HaGRID 最低接受信心值。
         */
        private const val MIN_GESTURE_CONFIDENCE = 0.70f
    }


    /*
     * CameraX analyzer thread
     */
    private lateinit var cameraExecutor: ExecutorService


    /*
     * CameraX
     */
    private lateinit var cameraController: CameraController


    /*
     * MediaPipe
     *
     * 目前只負責判斷：
     * 畫面中有沒有手。
     */
    private lateinit var handDetector: MediaPipeHandDetector


    /*
     * HaGRID ONNX
     *
     * 負責靜態手勢分類。
     */
    private lateinit var haGridClassifier: HaGridOnnxClassifier


    /*
     * 手勢防抖。
     */
    private val gestureStabilizer =
        GestureStabilizer(
            gestureConfirmCount = 2,
            unknownConfirmCount = 4
        )


    /*
     * MediaPipe 是否有偵測到手。
     */
    @Volatile
    private var handDetected = false


    /*
     * HaGRID frame counter。
     */
    private var haGridFrameCounter = 0


    /*
     * Compose Camera 權限狀態。
     */
    private var cameraPermissionGranted by
    mutableStateOf(false)


    /*
     * ML 結果回傳給 Compose。
     */
    private var gestureCallback:
            ((String, Float) -> Unit)? = null


    /*
     * Camera permission launcher
     */
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            cameraPermissionGranted = granted

            if (granted) {
                Log.d(TAG, "Camera permission granted")
            } else {
                Log.e(TAG, "Camera permission denied")
            }
        }


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setupFullScreen()

        cameraPermissionGranted =
            hasCameraPermission()

        setupGestureComponents()

        setupCamera()

        setupCompose()

        if (!cameraPermissionGranted) {
            requestCameraPermission()
        }
    }


    /*
     * ==============================
     * Full Screen
     * ==============================
     */

    private fun setupFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).hide(
            WindowInsetsCompat.Type.systemBars()
        )
    }


    /*
     * ==============================
     * Gesture / ML Setup
     * ==============================
     */

    private fun setupGestureComponents() {
        /*
         * HaGRID
         */
        haGridClassifier =
            HaGridOnnxClassifier(this)


        /*
         * MediaPipe
         */
        handDetector =
            MediaPipeHandDetector(
                context = this,
                onHandDetected = { detected ->
                    onHandDetectionChanged(
                        detected
                    )
                }
            )
    }


    /*
     * MediaPipe 偵測結果。
     */
    private fun onHandDetectionChanged(
        detected: Boolean
    ) {
        handDetected = detected

        if (!detected) {
            /*
             * 手離開鏡頭後：
             *
             * 清除上一個穩定手勢。
             */
            gestureStabilizer.reset()

            runOnUiThread {
                gestureCallback?.invoke(
                    "NO HAND",
                    0f
                )
            }
        }
    }


    /*
     * ==============================
     * Camera Setup
     * ==============================
     */

    private fun setupCamera() {
        cameraExecutor =
            Executors.newSingleThreadExecutor()

        cameraController =
            CameraController(
                context = this,
                analyzerExecutor = cameraExecutor,
                analyzer = ::analyzeImage
            )
    }


    /*
     * ==============================
     * Compose
     * ==============================
     */

    private fun setupCompose() {
        setContent {
            GestureTestTheme {
                var gestureName by remember {
                    mutableStateOf("NO HAND")
                }

                var confidence by remember {
                    mutableFloatStateOf(0f)
                }

                CameraScreen(
                    gestureName = gestureName,
                    confidence = confidence,
                    cameraPermissionGranted =
                        cameraPermissionGranted,
                    onStartCamera = {
                            lifecycleOwner,
                            previewView ->

                        /*
                         * Compose callback
                         */
                        gestureCallback =
                            { name, score ->
                                gestureName = name
                                confidence = score
                            }

                        cameraController.start(
                            lifecycleOwner = lifecycleOwner,
                            previewView = previewView
                        )
                    }
                )
            }
        }
    }


    /*
     * ==============================
     * Camera Frame
     * ==============================
     */

    private fun analyzeImage(
        imageProxy: ImageProxy
    ) {
        try {
            /*
             * 1.
             *
             * MediaPipe：
             * 判斷有沒有手。
             */
            handDetector.analyze(
                imageProxy
            )


            /*
             * 2.
             *
             * HaGRID 不需要每個 frame 都執行。
             */
            haGridFrameCounter++


            if (
                handDetected &&
                haGridFrameCounter %
                HAGRID_FRAME_INTERVAL == 0
            ) {
                runHaGrid(
                    imageProxy
                )
            }

        } catch (e: Exception) {
            Log.e(
                TAG,
                "Image analyze failed",
                e
            )

        } finally {
            /*
             * CameraX ImageProxy
             * 一定要關閉。
             */
            imageProxy.close()
        }
    }


    /*
     * ==============================
     * HaGRID
     * ==============================
     */

    private fun runHaGrid(
        imageProxy: ImageProxy
    ) {
        var bitmap =
            imageProxy.toBitmap()

        try {
            /*
             * Camera frame 旋轉。
             */
            bitmap =
                rotateBitmapIfNeeded(
                    bitmap = bitmap,
                    rotationDegrees =
                        imageProxy
                            .imageInfo
                            .rotationDegrees
                )


            /*
             * ONNX inference
             */
            val result =
                haGridClassifier.classify(
                    bitmap
                )


            /*
             * Confidence filter
             */
            val filteredLabel =
                filterGesture(
                    label = result.label,
                    confidence =
                        result.confidence
                )


            /*
             * Temporal smoothing
             */
            val stableLabel =
                gestureStabilizer
                    .stabilize(
                        filteredLabel
                    )


            Log.d(
                "HaGrid",
                "raw=${result.label}, " +
                        "confidence=${result.confidence}, " +
                        "filtered=$filteredLabel, " +
                        "stable=$stableLabel"
            )


            runOnUiThread {
                gestureCallback?.invoke(
                    stableLabel,
                    result.confidence
                )
            }

        } catch (e: Exception) {
            Log.e(
                "HaGrid",
                "HaGRID inference failed",
                e
            )

        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }


    /*
     * HaGRID confidence filter。
     */
    private fun filterGesture(
        label: String,
        confidence: Float
    ): String {
        return when {
            label == "no_gesture" ->
                "UNKNOWN"

            confidence <
                    MIN_GESTURE_CONFIDENCE ->
                "UNKNOWN"

            else ->
                label
        }
    }


    /*
     * Camera frame orientation。
     */
    private fun rotateBitmapIfNeeded(
        bitmap: Bitmap,
        rotationDegrees: Int
    ): Bitmap {
        if (rotationDegrees == 0) {
            return bitmap
        }

        val matrix =
            Matrix().apply {
                postRotate(
                    rotationDegrees.toFloat()
                )
            }

        val rotatedBitmap =
            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )

        /*
         * createBitmap 產生新物件後，
         * 可以把舊 Bitmap 回收。
         */
        if (rotatedBitmap !== bitmap) {
            bitmap.recycle()
        }

        return rotatedBitmap
    }


    /*
     * ==============================
     * Camera Permission
     * ==============================
     */

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }


    private fun requestCameraPermission() {
        requestPermissionLauncher.launch(
            Manifest.permission.CAMERA
        )
    }


    /*
     * ==============================
     * Destroy
     * ==============================
     */

    override fun onDestroy() {
        super.onDestroy()

        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }

        if (::handDetector.isInitialized) {
            handDetector.close()
        }

        if (::haGridClassifier.isInitialized) {
            haGridClassifier.close()
        }
    }
}
