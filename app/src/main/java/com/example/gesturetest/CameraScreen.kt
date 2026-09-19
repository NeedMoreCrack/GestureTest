package com.example.gesturetest

import androidx.camera.view.PreviewView

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut

import androidx.compose.foundation.background
import androidx.compose.foundation.border

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner


private data class DemoUiTarget(
    val name: String, val centerY: Float
)


@Composable
fun CameraScreen(
    input: GestureInputController.GestureInput,

    uiVisible: Boolean,

    selectedAction: String,

    cameraPermissionGranted: Boolean,

    onStartCamera: (
        LifecycleOwner, PreviewView
    ) -> Unit,

    onOpenUi: () -> Unit,

    onCloseUi: () -> Unit,

    onUiAction: (String) -> Unit
) {

    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current

    /*
     * ==========================
     * UI Gesture Controller
     * ==========================
     */

    val uiGestureController = remember {

        UiGestureController(
            requiredConfirmFrames = 2,

            minGestureConfidence = 0.65f
        )
    }

    /*
     * Pinch 已經 Click 過。
     *
     * 一次完整 Pinch
     * 最多只能 Click 一次。
     */
    var pinchClickLocked by remember {

        mutableStateOf(
            false
        )
    }

    /*
     * ==========================
     * Camera Preview
     * ==========================
     */

    val previewView = remember {

        PreviewView(context).apply {

            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    /*
     * ==========================
     * Demo Buttons
     * ==========================
     */

    val targets = remember {

        listOf(

            DemoUiTarget(
                name = "START GAME",

                centerY = 0.35f
            ),

            DemoUiTarget(
                name = "INVENTORY",

                centerY = 0.52f
            ),

            DemoUiTarget(
                name = "SETTINGS",

                centerY = 0.69f
            )
        )
    }

    val buttonLeft = 0.21f

    val buttonRight = 0.79f

    val buttonHalfHeight = 0.055f

    /*
     * ==========================
     * UI Gesture Sequence
     * ==========================
     *
     * 每次 input 更新都送進去。
     *
     * ✊ Closed_Fist
     * ->
     * ✋ Open_Palm
     * =
     * OPEN
     *
     * ✋ Open_Palm
     * ->
     * ✊ Closed_Fist
     * =
     * CLOSE
     */

    LaunchedEffect(
        input.gestureName, input.gestureConfidence, input.cursorX, input.cursorY, uiVisible
    ) {

        val event = uiGestureController.process(
            gestureName = input.gestureName,

            gestureConfidence = input.gestureConfidence,

            uiVisible = uiVisible
        )

        when (event) {

            UiGestureController.UiEvent.OPEN_UI -> {

                onOpenUi()
            }

            UiGestureController.UiEvent.CLOSE_UI -> {

                onCloseUi()

                /*
                 * 關閉 UI 時順便解除
                 * Pinch Click Lock。
                 */
                pinchClickLocked = false
            }

            UiGestureController.UiEvent.NONE -> {

                /*
                 * 不處理。
                 */
            }
        }
    }

    /*
     * ==========================
     * Hover
     * ==========================
     */

    val hoveredTarget =

        if (uiVisible && input.cursorVisible && input.cursorControlEnabled) {

            targets.firstOrNull { target ->

                input.cursorX in buttonLeft..buttonRight

                        &&

                        input.cursorY in (target.centerY - buttonHalfHeight)..(target.centerY + buttonHalfHeight)
            }

        } else {

            null
        }

    /*
     * ==========================
     * PINCH CLICK
     * ==========================
     *
     * 不再使用：
     *
     * PINCH_START
     *
     * 而是必須：
     *
     * PINCH_HOLD
     * +
     * Pinch Strength >= 90%
     *
     * 才能 Click。
     */

    LaunchedEffect(
        input.event, input.pinchStrength, input.isPinching, hoveredTarget?.name, uiVisible
    ) {

        /*
         * Pinch 已經放開。
         *
         * 下一次可以重新 Click。
         */
        if (input.event == GestureInputController.GestureEvent.PINCH_RELEASE

            ||

            !input.isPinching
        ) {

            pinchClickLocked = false

            return@LaunchedEffect
        }

        /*
         * 必須是 PINCH_HOLD。
         *
         * PINCH_START 絕對不 Click。
         */
        val validPinchClick =

            uiVisible

                    &&

                    hoveredTarget != null

                    &&

                    input.event == GestureInputController.GestureEvent.PINCH_HOLD

                    &&

                    input.pinchStrength >= 0.90f

                    &&

                    !pinchClickLocked

        if (validPinchClick) {

            hoveredTarget?.let { target ->

                /*
                 * 先 Lock，
                 * 防止 callback 後的 recomposition
                 * 又 Click 一次。
                 */
                pinchClickLocked = true

                onUiAction(
                    target.name
                )
            }
        }
    }

    /*
     * ==========================
     * Camera
     * ==========================
     */

    LaunchedEffect(
        cameraPermissionGranted
    ) {

        if (cameraPermissionGranted) {

            onStartCamera(
                lifecycleOwner, previewView
            )
        }
    }

    /*
     * ==========================
     * UI
     * ==========================
     */

    BoxWithConstraints(

        modifier = Modifier.fillMaxSize()

    ) {

        val screenWidth = maxWidth

        val screenHeight = maxHeight

        /*
         * Camera Preview。
         */
        AndroidView(

            modifier = Modifier.fillMaxSize(),

            factory = {

                previewView
            })

        /*
         * ==========================
         * Demo Menu
         * ==========================
         */

        AnimatedVisibility(

            visible = uiVisible,

            enter = fadeIn() + scaleIn(),

            exit = fadeOut() + scaleOut()

        ) {

            Box(
                modifier = Modifier.fillMaxSize()
            ) {

                targets.forEach { target ->

                    val isHovered =

                        hoveredTarget?.name == target.name

                    DemoButton(
                        text = target.name,

                        hovered = isHovered,

                        modifier = Modifier

                            .offset(
                                x = screenWidth * 0.21f,

                                y = screenHeight * (target.centerY - buttonHalfHeight)
                            )

                            .size(
                                width = screenWidth * 0.58f,

                                height = screenHeight * (buttonHalfHeight * 2f)
                            )
                    )
                }
            }
        }

        /*
         * ==========================
         * Cursor
         * ==========================
         */

        if (uiVisible && input.cursorVisible) {

            val cursorSize = 28.dp

            val x = input.cursorX.coerceIn(
                0f, 1f
            )

            val y = input.cursorY.coerceIn(
                0f, 1f
            )

            Box(

                modifier = Modifier

                    .offset(
                        x = screenWidth * x - cursorSize / 2,

                        y = screenHeight * y - cursorSize / 2
                    )

                    .size(
                        cursorSize
                    )

                    .background(
                        color =

                            when {

                                /*
                                 * >= 90%
                                 * 顯示綠色。
                                 */
                                input.pinchStrength >= 0.90f -> {

                                    Color.Green
                                }

                                /*
                                 * Pinching，
                                 * 但還沒 90%。
                                 */
                                input.isPinching -> {

                                    Color.Yellow
                                }

                                input.cursorControlEnabled -> {

                                    Color.White
                                }

                                else -> {

                                    Color.Gray
                                }
                            },

                        shape = CircleShape
                    )

                    .border(
                        width = 3.dp,

                        color = Color.Black,

                        shape = CircleShape
                    )
            )
        }

        /*
         * ==========================
         * DEBUG HUD
         * ==========================
         */

        Box(

            modifier = Modifier

                .align(
                    Alignment.TopStart
                )

                .padding(
                    12.dp
                )

                .background(
                    color = Color.Black.copy(
                        alpha = 0.72f
                    ),

                    shape = RoundedCornerShape(
                        12.dp
                    )
                )
        ) {

            Column(

                modifier = Modifier.padding(
                    horizontal = 12.dp,

                    vertical = 10.dp
                )
            ) {

                /*
                 * ==========================
                 * HUD Width Placeholder
                 * ==========================
                 *
                 * 這行看不到，
                 * 但會參與 Compose Layout 計算。
                 *
                 * 所以 HUD 一開始就會保留：
                 *
                 * Candidate: Closed_Fist (1/2)
                 *
                 * 所需要的寬度。
                 *
                 * Candidate 後續從 NONE ->
                 * Closed_Fist (1/2)
                 * 就不會造成背景左右跳動。
                 */
                Text(
                    text = "Candidate: Closed_Fist (1/2)",
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier.alpha(0f)
                )

                Text(
                    text = "Gesture: ${input.gestureName}", color = Color.White, fontSize = 15.sp
                )

                Text(
                    text = "Confidence: " + String.format(
                        "%.0f%%", input.gestureConfidence * 100f
                    ), color = Color.White, fontSize = 15.sp
                )

                Text(
                    text = "Stable: " + uiGestureController.getPreviousStableGesture(),
                    color = Color.White,
                    fontSize = 15.sp
                )

                Text(
                    text = "Candidate: " + uiGestureController.getCandidateGesture(),

                    color = Color.White,

                    fontSize = 15.sp
                )


                Text(
                    text = "Detect Progress: " + uiGestureController.getCandidateProgress(),

                    color = Color.White,

                    fontSize = 15.sp
                )

                Text(
                    text = "UI: " + if (uiVisible) {
                        "OPEN"
                    } else {
                        "CLOSED"
                    }, color = Color.White, fontSize = 15.sp
                )

                Text(
                    text = "Event: ${input.event}", color = Color.White, fontSize = 15.sp
                )

                Text(
                    text = "Pinching: ${input.isPinching}", color = Color.White, fontSize = 15.sp
                )

                Text(
                    text = "Pinch Ratio: " + String.format(
                        "%.2f", input.pinchRatio
                    ), color = Color.White, fontSize = 15.sp
                )

                Text(
                    text = "Pinch Strength: " + String.format(
                        "%.0f%%", input.pinchStrength * 100f
                    ), color = if (input.pinchStrength >= 0.90f) {
                        Color.Green
                    } else {
                        Color.White
                    }, fontSize = 15.sp
                )

                Text(
                    text = "Click Lock: $pinchClickLocked", color = Color.White, fontSize = 15.sp
                )

                Text(
                    text = "Rotation: ${input.rotationDegrees}",
                    color = Color.White,
                    fontSize = 15.sp
                )

                Text(
                    text = "Hover: " + (hoveredTarget?.name ?: "NONE"),
                    color = Color.White,
                    fontSize = 15.sp
                )

                Text(
                    text = "Last Action: $selectedAction", color = Color.White, fontSize = 15.sp
                )
            }
        }
    }
}


@Composable
private fun DemoButton(
    text: String, hovered: Boolean, modifier: Modifier = Modifier
) {

    val scale by animateFloatAsState(
        targetValue = if (hovered) {
            1.12f
        } else {
            1f
        },

        label = "DemoButtonScale"
    )

    Box(

        modifier = modifier

            .scale(
                scale
            )

            .background(
                color = if (hovered) {

                    Color.White.copy(
                        alpha = 0.90f
                    )

                } else {

                    Color.Black.copy(
                        alpha = 0.60f
                    )
                },

                shape = RoundedCornerShape(
                    20.dp
                )
            )

            .border(
                width = if (hovered) {
                    3.dp
                } else {
                    1.dp
                },

                color = Color.White,

                shape = RoundedCornerShape(
                    20.dp
                )
            ),

        contentAlignment = Alignment.Center
    ) {

        Text(
            text = text,

            color = if (hovered) {
                Color.Black
            } else {
                Color.White
            },

            fontSize = 22.sp,

            fontWeight = FontWeight.Bold
        )
    }
}
