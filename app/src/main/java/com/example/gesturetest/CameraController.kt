package com.example.gesturetest.camera

import android.content.Context
import android.util.Log

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

import java.util.concurrent.Executor


class CameraController(
    private val context: Context,
    private val analyzerExecutor: Executor,
    private val analyzer: (ImageProxy) -> Unit
) {

    companion object {
        private const val TAG = "CameraController"
    }


    fun start(
        lifecycleOwner: LifecycleOwner, previewView: PreviewView
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(
            context
        )

        cameraProviderFuture.addListener(
            {
                try {
                    val cameraProvider = cameraProviderFuture.get()

                    /*
                     * Camera preview
                     */
                    val preview = Preview.Builder().build().apply {
                        setSurfaceProvider(
                            previewView.surfaceProvider
                        )
                    }


                    /*
                     * Camera image analysis
                     */
                    val imageAnalysis = ImageAnalysis.Builder().setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    ).setOutputImageFormat(
                        ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
                    ).build()


                    imageAnalysis.setAnalyzer(
                        analyzerExecutor, analyzer
                    )


                    /*
                     * 使用後鏡頭。
                     */
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA


                    /*
                     * 清除舊的 binding。
                     */
                    cameraProvider.unbindAll()


                    /*
                     * 綁定 Camera。
                     */
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, cameraSelector, preview, imageAnalysis
                    )


                    Log.d(
                        TAG, "Camera started"
                    )

                } catch (e: Exception) {
                    Log.e(
                        TAG, "Camera start failed", e
                    )
                }
            }, ContextCompat.getMainExecutor(
                context
            )
        )
    }
}
