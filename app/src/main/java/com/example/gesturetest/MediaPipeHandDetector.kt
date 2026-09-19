package com.example.gesturetest

import android.content.Context
import android.os.SystemClock
import android.util.Log

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy

import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer


class MediaPipeHandDetector(
    context: Context,
    private val onHandResult:
        (HandDetectionResult?) -> Unit
) : AutoCloseable {

    companion object {

        private const val TAG =
            "MediaPipeHandDetector"

        private const val MODEL_NAME =
            "gesture_recognizer.task"
    }


    /*
     * MediaPipe Hand Landmark。
     */
    data class HandPoint(
        val x: Float,
        val y: Float,
        val z: Float
    )


    /*
     * 傳給 GestureInputController 的結果。
     */
    data class HandDetectionResult(

        val landmarks:
        List<HandPoint>,

        val gestureName:
        String,

        val gestureConfidence:
        Float,

        /*
         * CameraX 畫面旋轉角度。
         *
         * 通常：
         * 0 / 90 / 180 / 270
         */
        val rotationDegrees:
        Int
    )


    private val gestureRecognizer:
            GestureRecognizer


    /*
     * recognizeAsync() 是非同步的。
     *
     * setResultListener 在 init 裡，
     * 無法直接拿 analyze() 裡面的 local variable。
     *
     * 所以把目前 Camera Rotation
     * 暫存在 class field。
     */
    @Volatile
    private var currentRotationDegrees =
        0


    init {

        val baseOptions =
            BaseOptions
                .builder()
                .setModelAssetPath(
                    MODEL_NAME
                )
                .build()


        val options =
            GestureRecognizer
                .GestureRecognizerOptions
                .builder()

                .setBaseOptions(
                    baseOptions
                )

                .setRunningMode(
                    RunningMode.LIVE_STREAM
                )

                /*
                 * Demo 先只追蹤一隻手。
                 */
                .setNumHands(
                    1
                )

                .setMinHandDetectionConfidence(
                    0.5f
                )

                .setMinHandPresenceConfidence(
                    0.5f
                )

                .setMinTrackingConfidence(
                    0.5f
                )

                /*
                 * ==========================
                 * MediaPipe Result
                 * ==========================
                 */
                .setResultListener { result,
                                     _ ->


                    val hands =
                        result.landmarks()


                    /*
                     * 沒有偵測到手。
                     */
                    if (
                        hands.isEmpty()
                    ) {

                        onHandResult(
                            null
                        )

                        return@setResultListener
                    }


                    /*
                     * 目前只取第一隻手。
                     */
                    val hand =
                        hands.first()


                    /*
                     * MediaPipe Landmark
                     * ↓
                     * 我們自己的 HandPoint
                     */
                    val points =
                        hand.map {

                            HandPoint(
                                x =
                                    it.x(),

                                y =
                                    it.y(),

                                z =
                                    it.z()
                            )
                        }


                    /*
                     * 正常一隻手應該有
                     * 21 個 landmark。
                     */
                    if (
                        points.size < 21
                    ) {

                        Log.w(
                            TAG,
                            "Invalid landmark count: ${points.size}"
                        )


                        onHandResult(
                            null
                        )


                        return@setResultListener
                    }


                    /*
                     * ==========================
                     * Gesture Classification
                     * ==========================
                     */

                    val gestureGroups =
                        result.gestures()


                    var gestureName =
                        "UNKNOWN"


                    var gestureConfidence =
                        0f


                    if (
                        gestureGroups.isNotEmpty()
                        &&
                        gestureGroups
                            .first()
                            .isNotEmpty()
                    ) {

                        val bestGesture =
                            gestureGroups
                                .first()
                                .first()


                        gestureName =
                            bestGesture
                                .categoryName()


                        gestureConfidence =
                            bestGesture
                                .score()
                    }


                    /*
                     * ==========================
                     * 回傳給 MainActivity
                     * ==========================
                     */

                    onHandResult(

                        HandDetectionResult(

                            landmarks =
                                points,

                            gestureName =
                                gestureName,

                            gestureConfidence =
                                gestureConfidence,

                            /*
                             * 使用 analyze()
                             * 最近取得的 Camera Rotation。
                             */
                            rotationDegrees =
                                currentRotationDegrees
                        )
                    )
                }

                /*
                 * ==========================
                 * Error
                 * ==========================
                 */
                .setErrorListener { error ->


                    Log.e(
                        TAG,
                        "MediaPipe error",
                        error
                    )
                }

                .build()


        gestureRecognizer =
            GestureRecognizer
                .createFromOptions(
                    context,
                    options
                )


        Log.d(
            TAG,
            "GestureRecognizer created"
        )
    }


    /*
     * ==============================
     * Camera Frame Analyze
     * ==============================
     */

    @OptIn(
        ExperimentalGetImage::class
    )
    fun analyze(
        imageProxy: ImageProxy
    ) {

        val mediaImage =
            imageProxy.image
                ?: return


        /*
         * CameraX 回傳目前 Frame
         * 相對於目標顯示方向的旋轉角度。
         */
        val rotationDegrees =
            imageProxy
                .imageInfo
                .rotationDegrees


        /*
         * 保存給 async callback 使用。
         */
        currentRotationDegrees =
            rotationDegrees


        /*
         * Android MediaImage
         * ↓
         * MediaPipe MPImage
         */
        val mpImage =
            MediaImageBuilder(
                mediaImage
            ).build()


        /*
         * 告訴 MediaPipe
         * 這張 Camera Frame 要旋轉多少。
         */
        val imageProcessingOptions =
            ImageProcessingOptions
                .builder()
                .setRotationDegrees(
                    rotationDegrees
                )
                .build()


        /*
         * Debug：
         * 如果你要確認手機方向，
         * 可以暫時打開這個 Log。
         */
        Log.v(
            TAG,
            "rotationDegrees=$rotationDegrees"
        )


        /*
         * LIVE_STREAM
         * 使用 recognizeAsync。
         */
        gestureRecognizer
            .recognizeAsync(
                mpImage,
                imageProcessingOptions,
                SystemClock.uptimeMillis()
            )
    }


    /*
     * ==============================
     * Close
     * ==============================
     */

    override fun close() {

        gestureRecognizer.close()


        Log.d(
            TAG,
            "GestureRecognizer closed"
        )
    }
}
