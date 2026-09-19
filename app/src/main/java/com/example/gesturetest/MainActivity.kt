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

import kotlin.math.abs


class MainActivity : ComponentActivity() {

    companion object {

        private const val TAG =
            "MainActivity"


        /*
         * 每 2 個 Camera frame
         * 執行一次 HaGRID。
         */
        private const val HAGRID_FRAME_INTERVAL =
            2


        /*
         * HaGRID 最低接受信心值。
         */
        private const val MIN_GESTURE_CONFIDENCE =
            0.70f


        /*
         * MediaPipe 必須連續幾次
         * 沒看到手，
         * 才正式判定 NO HAND。
         */
        private const val NO_HAND_CONFIRM_COUNT =
            5


        /*
         * confidence 變化小於這個值時，
         * 不重新刷新 UI。
         *
         * 0.01 = 1%
         */
        private const val CONFIDENCE_UPDATE_THRESHOLD =
            0.01f
    }


    /*
     * CameraX analyzer thread。
     */
    private lateinit var cameraExecutor:
            ExecutorService


    /*
     * CameraX controller。
     */
    private lateinit var cameraController:
            CameraController


    /*
     * MediaPipe：
     *
     * 只負責判斷畫面中
     * 有沒有手。
     */
    private lateinit var handDetector:
            MediaPipeHandDetector


    /*
     * HaGRID ONNX：
     *
     * 負責辨識實際手勢。
     */
    private lateinit var haGridClassifier:
            HaGridOnnxClassifier


    /*
     * 手勢 Temporal smoothing。
     */
    private val gestureStabilizer =
        GestureStabilizer(
            gestureConfirmCount = 2,
            unknownConfirmCount = 4
        )


    /*
     * MediaPipe 是否偵測到手。
     *
     * MediaPipe callback 與
     * Camera analyzer 可能不同 thread，
     * 所以使用 Volatile。
     */
    @Volatile
    private var handDetected =
        false


    /*
     * Camera frame counter。
     */
    private var haGridFrameCounter =
        0


    /*
     * 連續沒有偵測到手的 frame 數量。
     */
    private var noHandFrameCount =
        0


    /*
     * Compose Camera Permission。
     */
    private var cameraPermissionGranted by
    mutableStateOf(false)


    /*
     * ML -> Compose callback。
     */
    private var gestureCallback:
            ((String, Float) -> Unit)? =
        null


    /*
     * 最後一次真正送到 UI 的資料。
     *
     * 避免每個 frame 都讓 Compose
     * 重新 Recomposition。
     */
    private var lastDisplayedGesture =
        ""

    private var lastDisplayedConfidence =
        -1f


    /*
     * Camera permission launcher。
     */
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts
                .RequestPermission()
        ) { granted ->

            cameraPermissionGranted =
                granted


            if (granted) {

                Log.d(
                    TAG,
                    "Camera permission granted"
                )

            } else {

                Log.e(
                    TAG,
                    "Camera permission denied"
                )
            }
        }


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )


        setupFullScreen()


        cameraPermissionGranted =
            hasCameraPermission()


        setupGestureComponents()

        setupCamera()

        setupCompose()


        if (
            !cameraPermissionGranted
        ) {

            requestCameraPermission()
        }
    }


    /*
     * ==============================
     * Full Screen
     * ==============================
     */

    private fun setupFullScreen() {

        WindowCompat
            .setDecorFitsSystemWindows(
                window,
                false
            )


        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).hide(
            WindowInsetsCompat
                .Type
                .systemBars()
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
            HaGridOnnxClassifier(
                this
            )


        /*
         * MediaPipe
         *
         * 注意：
         *
         * MediaPipe 不再直接更新 UI。
         *
         * 它只改 handDetected。
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

        /*
         * 只更新狀態。
         *
         * 絕對不要在這裡：
         *
         * gestureCallback.invoke()
         *
         * 否則 MediaPipe 與 HaGRID
         * 會同時搶著更新 UI。
         */
        handDetected =
            detected
    }


    /*
     * ==============================
     * Camera Setup
     * ==============================
     */

    private fun setupCamera() {

        cameraExecutor =
            Executors
                .newSingleThreadExecutor()


        cameraController =
            CameraController(
                context = this,

                analyzerExecutor =
                    cameraExecutor,

                analyzer =
                    ::analyzeImage
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

                var gestureName by
                remember {

                    mutableStateOf(
                        "NO HAND"
                    )
                }


                var confidence by
                remember {

                    mutableFloatStateOf(
                        0f
                    )
                }


                CameraScreen(

                    gestureName =
                        gestureName,

                    confidence =
                        confidence,

                    cameraPermissionGranted =
                        cameraPermissionGranted,

                    onStartCamera = {
                            lifecycleOwner,
                            previewView ->


                        /*
                         * Compose callback。
                         *
                         * 整個程式只有
                         * publishGesture()
                         * 可以透過這裡改畫面。
                         */
                        gestureCallback =
                            { name, score ->

                                gestureName =
                                    name

                                confidence =
                                    score
                            }


                        cameraController.start(
                            lifecycleOwner =
                                lifecycleOwner,

                            previewView =
                                previewView
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
             *
             * 非同步判斷有沒有手。
             */
            handDetector.analyze(
                imageProxy
            )


            /*
             * 2.
             *
             * HaGRID 不需要每 frame
             * 都執行。
             */
            haGridFrameCounter++


            /*
             * 有手。
             */
            if (
                handDetected
            ) {

                /*
                 * 一旦重新看到手，
                 * 清除 NO HAND 計數。
                 */
                noHandFrameCount =
                    0


                /*
                 * 每 N frame
                 * 執行一次 HaGRID。
                 */
                if (
                    haGridFrameCounter %
                    HAGRID_FRAME_INTERVAL ==
                    0
                ) {

                    runHaGrid(
                        imageProxy
                    )
                }

            } else {

                /*
                 * MediaPipe 沒看到手。
                 *
                 * 不立即切 NO HAND，
                 * 先累積次數。
                 */
                noHandFrameCount++


                if (
                    noHandFrameCount >=
                    NO_HAND_CONFIRM_COUNT
                ) {

                    /*
                     * 正式確認沒有手。
                     */
                    val resetResult =
                        gestureStabilizer
                            .reset()


                    publishGesture(
                        gesture =
                            resetResult.label,

                        confidence =
                            resetResult.confidence
                    )
                }
            }

        } catch (
            e: Exception
        ) {

            Log.e(
                TAG,
                "Image analyze failed",
                e
            )

        } finally {

            /*
             * CameraX ImageProxy
             * 一定要 close。
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
             * Camera orientation。
             */
            bitmap =
                rotateBitmapIfNeeded(
                    bitmap =
                        bitmap,

                    rotationDegrees =
                        imageProxy
                            .imageInfo
                            .rotationDegrees
                )


            /*
             * ONNX inference。
             */
            val result =
                haGridClassifier
                    .classify(
                        bitmap
                    )


            /*
             * Confidence filter。
             */
            val filteredLabel =
                filterGesture(
                    label =
                        result.label,

                    confidence =
                        result.confidence
                )


            /*
             * Temporal smoothing。
             *
             * 現在 label 與 confidence
             * 是一起保存的。
             */
            val stableResult =
                gestureStabilizer
                    .stabilize(
                        newGesture =
                            filteredLabel,

                        confidence =
                            result.confidence
                    )


            Log.d(
                "HaGrid",

                "raw=${result.label}, " +
                        "confidence=${result.confidence}, " +
                        "filtered=$filteredLabel, " +
                        "stable=${stableResult.label}, " +
                        "stableConfidence=${stableResult.confidence}"
            )


            /*
             * 唯一 UI 更新入口。
             */
            publishGesture(
                gesture =
                    stableResult.label,

                confidence =
                    stableResult.confidence
            )

        } catch (
            e: Exception
        ) {

            Log.e(
                "HaGrid",
                "HaGRID inference failed",
                e
            )

        } finally {

            if (
                !bitmap.isRecycled
            ) {

                bitmap.recycle()
            }
        }
    }


    /*
     * ==============================
     * UI Publish
     * ==============================
     */

    private fun publishGesture(
        gesture: String,
        confidence: Float
    ) {

        /*
         * 手勢沒有改變，
         * 而 confidence 變化小於 1%，
         *
         * 不需要重新更新 Compose。
         */
        if (
            gesture ==
            lastDisplayedGesture
            &&
            abs(
                confidence -
                        lastDisplayedConfidence
            ) <
            CONFIDENCE_UPDATE_THRESHOLD
        ) {

            return
        }


        lastDisplayedGesture =
            gesture

        lastDisplayedConfidence =
            confidence


        runOnUiThread {

            gestureCallback
                ?.invoke(
                    gesture,
                    confidence
                )
        }
    }


    /*
     * ==============================
     * HaGRID Filter
     * ==============================
     */

    private fun filterGesture(
        label: String,
        confidence: Float
    ): String {

        return when {

            /*
             * 模型自己判定沒有明確手勢。
             */
            label ==
                    "no_gesture" -> {

                "UNKNOWN"
            }


            /*
             * Confidence 太低。
             */
            confidence <
                    MIN_GESTURE_CONFIDENCE -> {

                "UNKNOWN"
            }


            else -> {

                label
            }
        }
    }


    /*
     * ==============================
     * Bitmap Rotation
     * ==============================
     */

    private fun rotateBitmapIfNeeded(
        bitmap: Bitmap,
        rotationDegrees: Int
    ): Bitmap {

        if (
            rotationDegrees ==
            0
        ) {

            return bitmap
        }


        val matrix =
            Matrix().apply {

                postRotate(
                    rotationDegrees
                        .toFloat()
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
         * createBitmap 建立新 Bitmap，
         * 舊的可以回收。
         */
        if (
            rotatedBitmap !==
            bitmap
        ) {

            bitmap.recycle()
        }


        return rotatedBitmap
    }


    /*
     * ==============================
     * Camera Permission
     * ==============================
     */

    private fun hasCameraPermission():
            Boolean {

        return ContextCompat
            .checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) ==
                PackageManager
                    .PERMISSION_GRANTED
    }


    private fun requestCameraPermission() {

        requestPermissionLauncher
            .launch(
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


        if (
            ::cameraExecutor
                .isInitialized
        ) {

            cameraExecutor.shutdown()
        }


        if (
            ::handDetector
                .isInitialized
        ) {

            handDetector.close()
        }


        if (
            ::haGridClassifier
                .isInitialized
        ) {

            haGridClassifier.close()
        }
    }
}
