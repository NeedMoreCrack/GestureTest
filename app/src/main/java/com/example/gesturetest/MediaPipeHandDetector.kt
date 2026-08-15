package com.example.gesturetest.gesture

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
    private val onHandDetected:
        (Boolean) -> Unit
) : AutoCloseable {

    companion object {
        private const val TAG =
            "MediaPipeHandDetector"

        private const val MODEL_NAME =
            "gesture_recognizer.task"
    }


    private val gestureRecognizer:
            GestureRecognizer


    init {
        val baseOptions =
            BaseOptions.builder()
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
                .setMinHandDetectionConfidence(
                    0.5f
                )
                .setMinHandPresenceConfidence(
                    0.5f
                )
                .setMinTrackingConfidence(
                    0.5f
                )
                .setResultListener {
                        result,
                        _ ->

                    /*
                     * landmarks() 有資料
                     * 代表 MediaPipe 有看到手。
                     */
                    val detected =
                        result
                            .landmarks()
                            .isNotEmpty()

                    onHandDetected(
                        detected
                    )
                }
                .setErrorListener {
                        error ->

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
    }


    @OptIn(
        ExperimentalGetImage::class
    )
    fun analyze(
        imageProxy: ImageProxy
    ) {
        val mediaImage =
            imageProxy.image
                ?: return


        val mpImage =
            MediaImageBuilder(
                mediaImage
            ).build()


        val rotationDegrees =
            imageProxy
                .imageInfo
                .rotationDegrees


        val imageProcessingOptions =
            ImageProcessingOptions
                .builder()
                .setRotationDegrees(
                    rotationDegrees
                )
                .build()


        gestureRecognizer
            .recognizeAsync(
                mpImage,
                imageProcessingOptions,
                SystemClock.uptimeMillis()
            )
    }


    override fun close() {
        gestureRecognizer.close()
    }
}
