package com.example.gesturetest.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner


@Composable
fun CameraScreen(
    gestureName: String,
    confidence: Float,
    cameraPermissionGranted: Boolean,
    onStartCamera: (
        LifecycleOwner,
        PreviewView
    ) -> Unit
) {
    val context =
        LocalContext.current

    val lifecycleOwner =
        LocalLifecycleOwner.current


    /*
     * PreviewView 只建立一次。
     */
    val previewView =
        remember {
            PreviewView(context)
        }


    /*
     * 第一次授權 Camera 後，
     * cameraPermissionGranted
     * 由 false → true，
     *
     * LaunchedEffect 會重新執行。
     */
    LaunchedEffect(
        cameraPermissionGranted
    ) {
        if (
            cameraPermissionGranted
        ) {
            onStartCamera(
                lifecycleOwner,
                previewView
            )
        }
    }


    Box(
        modifier =
            Modifier.fillMaxSize()
    ) {

        /*
         * Camera Preview
         */
        AndroidView(
            modifier =
                Modifier.fillMaxSize(),
            factory = {
                previewView
            }
        )


        /*
         * Gesture HUD
         */
        Column(
            modifier =
                Modifier.align(
                    Alignment.Center
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Text(
                text = gestureName,
                fontSize = 32.sp
            )


            Text(
                text =
                    "${(confidence * 100).toInt()}%"
            )
        }
    }
}
