package com.example.gesturetest

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

import androidx.camera.core.ImageProxy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.core.content.ContextCompat

import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.gesturetest.camera.CameraController

import com.example.gesturetest.ui.theme.GestureTestTheme

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity :
    ComponentActivity() {

    companion object {

        private const val TAG =
            "MainActivity"
    }

    /*
     * Camera analyzer thread。
     */
    private lateinit var cameraExecutor:
            ExecutorService

    /*
     * CameraX。
     */
    private lateinit var cameraController:
            CameraController

    /*
     * MediaPipe。
     */
    private lateinit var handDetector:
            MediaPipeHandDetector

    /*
     * Cursor / Pinch Controller。
     */
    private lateinit var gestureInputController:
            GestureInputController

    /*
     * MediaPipe thread
     * ->
     * Compose State callback。
     */
    private var gestureInputCallback:
            (
                (
                GestureInputController
                .GestureInput
            ) -> Unit
            )? =
        null

    /*
     * ==========================
     * Camera Permission
     * ==========================
     */

    private var cameraPermissionGranted by
    mutableStateOf(
        false
    )

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts
                .RequestPermission()
        ) {
                granted ->

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

    /*
     * ==========================
     * Lifecycle
     * ==========================
     */

    override fun onCreate(
        savedInstanceState:
        Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setupFullScreen()

        cameraPermissionGranted =
            hasCameraPermission()

        setupGesture()

        setupCamera()

        setupCompose()

        if (
            !cameraPermissionGranted
        ) {

            requestCameraPermission()
        }
    }

    /*
     * ==========================
     * Full Screen
     * ==========================
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
     * ==========================
     * Gesture
     * ==========================
     */

    private fun setupGesture() {

        gestureInputController =
            GestureInputController(
                /*
                 * Cursor smoothing。
                 */
                smoothingAlpha =
                    0.22f,

                /*
                 * 進入 Pinch。
                 */
                pinchStartThreshold =
                    0.45f,

                /*
                 * Pinch Release。
                 *
                 * 使用 hysteresis，
                 * 防止臨界值抖動。
                 */
                pinchReleaseThreshold =
                    0.60f,

                /*
                 * MediaPipe 短暫漏手
                 * 不馬上清 Cursor。
                 */
                noHandConfirmCount =
                    4,

                /*
                 * 後鏡頭。
                 */
                mirrorX =
                    false
            )

        /*
         * MediaPipe GestureRecognizer。
         */
        handDetector =
            MediaPipeHandDetector(
                context =
                    this,

                onHandResult = {
                        handResult ->

                    /*
                     * MediaPipe
                     * ↓
                     * GestureInputController
                     * ↓
                     * Cursor / Pinch / GestureInput
                     */
                    val input =
                        gestureInputController
                            .process(
                                handResult
                            )

                    publishGestureInput(
                        input
                    )
                }
            )
    }

    /*
     * ==========================
     * Camera
     * ==========================
     */

    private fun setupCamera() {

        cameraExecutor =
            Executors
                .newSingleThreadExecutor()

        cameraController =
            CameraController(
                context =
                    this,

                analyzerExecutor =
                    cameraExecutor,

                analyzer =
                    ::analyzeImage
            )
    }

    /*
     * ==========================
     * Compose
     * ==========================
     */

    private fun setupCompose() {

        setContent {

            GestureTestTheme {

                /*
                 * ==========================
                 * Gesture Input
                 * ==========================
                 */

                var gestureInput by
                remember {

                    mutableStateOf(

                        GestureInputController
                            .GestureInput(
                                cursorX =
                                    0.5f,

                                cursorY =
                                    0.5f,

                                cursorVisible =
                                    false,

                                cursorControlEnabled =
                                    false,

                                isPinching =
                                    false,

                                event =
                                    GestureInputController
                                        .GestureEvent
                                        .NO_HAND,

                                pinchRatio =
                                    1f,

                                pinchStrength =
                                    0f,

                                gestureName =
                                    "NO HAND",

                                gestureConfidence =
                                    0f,

                                backHandOpen =
                                    false,

                                rotationDegrees =
                                    0
                            )
                    )
                }

                /*
                 * UI 是否開啟。
                 */
                var uiVisible by
                remember {

                    mutableStateOf(
                        false
                    )
                }

                /*
                 * 最後點擊的 Button。
                 */
                var selectedAction by
                remember {

                    mutableStateOf(
                        "NONE"
                    )
                }

                CameraScreen(
                    input =
                        gestureInput,

                    uiVisible =
                        uiVisible,

                    selectedAction =
                        selectedAction,

                    cameraPermissionGranted =
                        cameraPermissionGranted,

                    /*
                     * ==========================
                     * Camera
                     * ==========================
                     */
                    onStartCamera = {
                            lifecycleOwner,
                            previewView ->

                        /*
                         * MediaPipe Result
                         * 傳入 Compose State。
                         */
                        gestureInputCallback =
                            {
                                    newInput ->

                                gestureInput =
                                    newInput
                            }

                        cameraController
                            .start(
                                lifecycleOwner =
                                    lifecycleOwner,

                                previewView =
                                    previewView
                            )
                    },

                    /*
                     * ==========================
                     * OPEN UI
                     * ==========================
                     *
                     * 真正觸發條件由
                     * UiGestureController 控制：
                     *
                     * ✊ -> ✋
                     */
                    onOpenUi = {

                        if (!uiVisible) {

                            uiVisible =
                                true

                            Log.d(
                                TAG,
                                "UI OPEN"
                            )
                        }
                    },

                    /*
                     * ==========================
                     * CLOSE UI
                     * ==========================
                     *
                     * ✋ -> ✊
                     */
                    onCloseUi = {

                        if (uiVisible) {

                            uiVisible =
                                false

                            Log.d(
                                TAG,
                                "UI CLOSE"
                            )
                        }
                    },

                    /*
                     * ==========================
                     * Button Click
                     * ==========================
                     */
                    onUiAction = {
                            action ->

                        selectedAction =
                            action

                        Log.d(
                            TAG,
                            "UI Action = $action"
                        )

                        Toast
                            .makeText(
                                this,
                                "Clicked: $action",
                                Toast.LENGTH_SHORT
                            )
                            .show()

                        when (action) {

                            "START GAME" -> {

                                Log.d(
                                    TAG,
                                    "START GAME clicked"
                                )
                            }

                            "INVENTORY" -> {

                                Log.d(
                                    TAG,
                                    "INVENTORY clicked"
                                )
                            }

                            "SETTINGS" -> {

                                Log.d(
                                    TAG,
                                    "SETTINGS clicked"
                                )
                            }
                        }
                    }
                )
            }
        }
    }

    /*
     * ==========================
     * Camera Frame
     * ==========================
     */

    private fun analyzeImage(
        imageProxy:
        ImageProxy
    ) {

        try {

            handDetector
                .analyze(
                    imageProxy
                )

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
     * ==========================
     * Publish Gesture
     * ==========================
     */

    private fun publishGestureInput(
        input:
        GestureInputController
        .GestureInput
    ) {

        /*
         * MediaPipe callback
         * 不一定在 Main Thread。
         */
        runOnUiThread {

            gestureInputCallback
                ?.invoke(
                    input
                )
        }
    }

    /*
     * ==========================
     * Permission
     * ==========================
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
     * ==========================
     * Destroy
     * ==========================
     */

    override fun onDestroy() {

        super.onDestroy()

        if (
            ::cameraExecutor
                .isInitialized
        ) {

            cameraExecutor
                .shutdown()
        }

        if (
            ::handDetector
                .isInitialized
        ) {

            handDetector
                .close()
        }

        if (
            ::gestureInputController
                .isInitialized
        ) {

            gestureInputController
                .reset()
        }
    }
}
