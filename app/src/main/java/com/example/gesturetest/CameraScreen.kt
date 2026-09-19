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
import androidx.compose.foundation.layout.width

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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

import kotlinx.coroutines.delay


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
     * =====================================================
     * UI Gesture Controller
     * =====================================================
     *
     * ✊ -> ✋
     * OPEN UI
     *
     * ✋ -> ✊
     * CLOSE UI
     */
    val uiGestureController = remember {

        UiGestureController(
            requiredConfirmFrames = 2, minGestureConfidence = 0.65f
        )
    }


    /*
     * =====================================================
     * Pinch Click Lock
     * =====================================================
     *
     * 一次完整 Pinch 只能 Click 一次。
     *
     * 必須放開後：
     *
     * PINCH_RELEASE
     *
     * 才能進行下一次 Click。
     */
    var pinchClickLocked by remember {

        mutableStateOf(
            false
        )
    }


    /*
     * =====================================================
     * Debug HUD 顯示狀態
     * =====================================================
     *
     * 預設顯示。
     *
     * 👍 Thumb_Up 1 秒
     * -> 顯示
     *
     * 👎 Thumb_Down 1 秒
     * -> 隱藏
     */
    var debugHudVisible by remember {

        mutableStateOf(
            true
        )
    }


    /*
     * =====================================================
     * Camera Preview
     * =====================================================
     */
    val previewView = remember {

        PreviewView(context).apply {

            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }


    /*
     * =====================================================
     * Demo UI Buttons
     * =====================================================
     */
    val targets = remember {

        listOf(

            DemoUiTarget(
                name = "START GAME", centerY = 0.35f
            ),

            DemoUiTarget(
                name = "INVENTORY", centerY = 0.52f
            ),

            DemoUiTarget(
                name = "SETTINGS", centerY = 0.69f
            )
        )
    }


    /*
     * Button normalized range。
     */
    val buttonLeft = 0.21f

    val buttonRight = 0.79f

    val buttonHalfHeight = 0.055f


    /*
     * =====================================================
     * Main UI Gesture Sequence
     * =====================================================
     *
     * UI CLOSED：
     *
     * ✊ Closed_Fist
     *       ↓
     * ✋ Open_Palm
     *       ↓
     * OPEN UI
     *
     *
     * UI OPEN：
     *
     * ✋ Open_Palm
     *       ↓
     * ✊ Closed_Fist
     *       ↓
     * CLOSE UI
     */
    LaunchedEffect(
        input, uiVisible
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
                 * UI 關閉時，
                 * Pinch Lock 一併 reset。
                 */
                pinchClickLocked = false
            }


            UiGestureController.UiEvent.NONE -> {

                /*
                 * 不做任何事情。
                 */
            }
        }
    }


    /*
     * =====================================================
     * Debug HUD Gesture
     * =====================================================
     *
     * 注意：
     *
     * 不直接把 gestureConfidence
     * 放進 LaunchedEffect Key。
     *
     * 因為 confidence 每幀可能：
     *
     * 75%
     * 82%
     * 79%
     * 91%
     *
     * 如果直接當 key，
     * Coroutine 會一直 Restart，
     * 永遠可能等不到完整 1 秒。
     */


    /*
     * 👍 Thumb Up
     */
    val validThumbUp =

        input.gestureName == "Thumb_Up"

                &&

                input.gestureConfidence >= 0.70f


    /*
     * 👎 Thumb Down
     */
    val validThumbDown =

        input.gestureName == "Thumb_Down"

                &&

                input.gestureConfidence >= 0.70f


    /*
     * =====================================================
     * 👍 Thumb Up 1 秒
     *
     * OPEN Debug HUD
     * =====================================================
     */
    LaunchedEffect(
        validThumbUp, debugHudVisible
    ) {

        /*
         * HUD 已經開啟時，
         * Thumb Up 不需要再處理。
         */
        if (validThumbUp && !debugHudVisible) {

            /*
             * 必須連續維持 1 秒。
             *
             * 如果中途：
             *
             * Thumb_Up -> UNKNOWN
             *
             * validThumbUp 會變成 false，
             * Compose 會取消這個 Coroutine。
             */
            delay(
                1000L
            )


            /*
             * 成功維持一秒。
             */
            debugHudVisible = true
        }
    }


    /*
     * =====================================================
     * 👎 Thumb Down 1 秒
     *
     * CLOSE Debug HUD
     * =====================================================
     */
    LaunchedEffect(
        validThumbDown, debugHudVisible
    ) {

        /*
         * HUD 關閉時，
         * Thumb Down 不需要再處理。
         */
        if (validThumbDown && debugHudVisible) {

            delay(
                1000L
            )


            /*
             * 成功維持一秒。
             */
            debugHudVisible = false
        }
    }


    /*
     * =====================================================
     * Hover
     * =====================================================
     */
    val hoveredTarget =

        if (uiVisible && input.cursorVisible && input.cursorControlEnabled) {

            targets.firstOrNull { target ->


                    /*
                     * Cursor X
                     * 必須進入 Button 範圍。
                     */
                    input.cursorX in buttonLeft..buttonRight


                            &&


                            /*
                             * Cursor Y
                             * 必須進入對應 Button。
                             */
                            input.cursorY in (target.centerY - buttonHalfHeight)..(target.centerY + buttonHalfHeight)
                }

        } else {

            null
        }


    /*
     * =====================================================
     * PINCH CLICK
     * =====================================================
     *
     * 舊版：
     *
     * PINCH_START
     * -> Click
     *
     * 太敏感。
     *
     *
     * 現在：
     *
     * PINCH_START
     * -> 不 Click
     *
     * PINCH_HOLD
     * +
     * Pinch Strength >= 90%
     * -> Click
     */
    LaunchedEffect(
        input.event, input.pinchStrength, input.isPinching, hoveredTarget?.name, uiVisible
    ) {

        /*
         * ==========================
         * Pinch Release
         * ==========================
         *
         * 使用者放開手指後，
         * 解除 Click Lock。
         */
        if (input.event == GestureInputController.GestureEvent.PINCH_RELEASE

            ||

            !input.isPinching
        ) {

            pinchClickLocked = false

            return@LaunchedEffect
        }


        /*
         * ==========================
         * Valid Click
         * ==========================
         */
        val validPinchClick =

            /*
             * UI 必須打開。
             */
            uiVisible

                    &&

                    /*
                     * Cursor 必須 Hover
                     * 在某一個 Button。
                     */
                    hoveredTarget != null

                    &&

                    /*
                     * 必須已經進入 HOLD。
                     *
                     * PINCH_START 不算。
                     */
                    input.event == GestureInputController.GestureEvent.PINCH_HOLD

                    &&

                    /*
                     * Pinch 完整程度
                     * >= 90%。
                     */
                    input.pinchStrength >= 0.90f

                    &&

                    /*
                     * 這一次 Pinch
                     * 還沒有 Click 過。
                     */
                    !pinchClickLocked


        if (validPinchClick) {

            hoveredTarget?.let { target ->


                    /*
                     * 一定先 Lock。
                     *
                     * 防止 callback /
                     * recomposition
                     * 造成重複 Click。
                     */
                    pinchClickLocked = true


                    /*
                     * 真正執行 Click。
                     */
                    onUiAction(
                        target.name
                    )
                }
        }
    }


    /*
     * =====================================================
     * Start Camera
     * =====================================================
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
     * =====================================================
     * Screen
     * =====================================================
     */
    BoxWithConstraints(

        modifier = Modifier.fillMaxSize()

    ) {

        /*
         * 保存 Screen size。
         *
         * 避免進入其他 Compose Scope
         * 後拿不到 maxWidth / maxHeight。
         */
        val screenWidth = maxWidth

        val screenHeight = maxHeight


        /*
         * =====================================================
         * Camera
         * =====================================================
         */
        AndroidView(

            modifier = Modifier.fillMaxSize(),

            factory = {

                previewView
            })


        /*
         * =====================================================
         * Main UI
         * =====================================================
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
         * =====================================================
         * Cursor
         * =====================================================
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
                                 * 90% Pinch
                                 * -> Green
                                 */
                                input.pinchStrength >= 0.90f -> {

                                    Color.Green
                                }


                                /*
                                 * 正在 Pinch
                                 * 但還不到 90%
                                 * -> Yellow
                                 */
                                input.isPinching -> {

                                    Color.Yellow
                                }


                                /*
                                 * Normal Cursor
                                 * -> White
                                 */
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
         * =====================================================
         * DEBUG HUD
         * =====================================================
         *
         * 👍 Thumb_Up 1 秒
         * -> SHOW
         *
         * 👎 Thumb_Down 1 秒
         * -> HIDE
         */
        if (debugHudVisible) {

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

                /*
                 * =================================================
                 * 固定 HUD Width
                 * =================================================
                 *
                 * 不再依照文字內容改變 Width。
                 *
                 * 所以：
                 *
                 * Candidate: NONE
                 *
                 * 變成：
                 *
                 * Candidate: Closed_Fist
                 *
                 * 背景也不會左右跳。
                 */
                Column(

                    modifier = Modifier

                        .width(
                            260.dp
                        )

                        .padding(
                            horizontal = 12.dp,

                            vertical = 10.dp
                        )
                ) {


                    /*
                     * ==========================
                     * Gesture
                     * ==========================
                     */
                    Text(
                        text = "Gesture: ${input.gestureName}",

                        color = Color.White,

                        fontSize = 15.sp
                    )


                    /*
                     * ==========================
                     * Confidence
                     * ==========================
                     */
                    Text(
                        text = "Confidence: " + String.format(
                            "%.0f%%", input.gestureConfidence * 100f
                        ),

                        color = Color.White,

                        fontSize = 15.sp
                    )


                    /*
                     * ==========================
                     * Stable Gesture
                     * ==========================
                     */
                    Text(
                        text = "Stable: " + uiGestureController.getPreviousStableGesture(),

                        color = Color.White,

                        fontSize = 15.sp
                    )


                    /*
                     * ==========================
                     * Candidate
                     * ==========================
                     *
                     * Gesture 名稱獨立一行。
                     */
                    Text(
                        text = "Candidate: " + uiGestureController.getCandidateGesture(),

                        color = Color.White,

                        fontSize = 15.sp
                    )


                    /*
                     * ==========================
                     * Detect Progress
                     * ==========================
                     *
                     * 1/2 不跟 Candidate
                     * 放在同一行。
                     */
                    Text(
                        text = "Detect Progress: " + uiGestureController.getCandidateProgress(),

                        color = Color.White,

                        fontSize = 15.sp
                    )


                    /*
                     * ==========================
                     * Main UI State
                     * ==========================
                     */
                    Text(
                        text = "UI: " + if (uiVisible) {

                            "OPEN"

                        } else {

                            "CLOSED"
                        },

                        color = Color.White,

                        fontSize = 15.sp
                    )


                    /*
                     * ==========================
                     * Cursor
                     * ==========================
                     */
                    Text(
                        text = "Cursor: " + if (input.cursorControlEnabled) {

                            "ACTIVE"

                        } else {

                            "LOCKED"
                        },

                        color = Color.White,

                        fontSize = 15.sp
                    )


                    /*
                     * ==========================
                     * Pinch Event
                     * ==========================
                     */
                    Text(
                        text = "Event: ${input.event}",

                        color = Color.White,

                        fontSize = 15.sp
                    )


                    /*
                     * ==========================
                     * Pinching
                     * ==========================
                     */
                    Text(
                        text = "Pinching: ${input.isPinching}",

                        color = Color.White,

                        fontSize = 15.sp
                    )


                    /*
                     * ==========================
                     * Pinch Ratio
                     * ==========================
                     */
                    Text(
                        text = "Pinch Ratio: " + String.format(
                            "%.2f", input.pinchRatio
                        ),

                        color = Color.White,

                        fontSize = 15.sp
                    )


                    /*
                     * ==========================
                     * Pinch Strength
                     * ==========================
                     */
                    Text(
                        text = "Pinch Strength: " + String.format(
                            "%.0f%%", input.pinchStrength * 100f
                        ),

                        color = if (input.pinchStrength >= 0.90f) {

                            Color.Green

                        } else {

                            Color.White
                        },

                        fontSize = 15.sp
                    )


                    /*
                     * ==========================
                     * Click Lock
                     * ==========================
                     */
                    Text(
                        text = "Click Lock: $pinchClickLocked",

                        color = Color.White,

                        fontSize = 15.sp
                    )


                    /*
                     * ==========================
                     * Rotation
                     * ==========================
                     */
                    Text(
                        text = "Rotation: ${input.rotationDegrees}",

                        color = Color.White,

                        fontSize = 15.sp
                    )


                    /*
                     * ==========================
                     * Back Hand
                     * ==========================
                     */
                    Text(
                        text = "Back Hand Open: ${input.backHandOpen}",

                        color = Color.White,

                        fontSize = 15.sp
                    )


                    /*
                     * ==========================
                     * Hover
                     * ==========================
                     */
                    Text(
                        text = "Hover: " + (hoveredTarget?.name ?: "NONE"),

                        color = Color.White,

                        fontSize = 15.sp
                    )


                    /*
                     * ==========================
                     * Last Action
                     * ==========================
                     */
                    Text(
                        text = "Last Action: $selectedAction",

                        color = Color.White,

                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}


/*
 * =====================================================
 * Demo Button
 * =====================================================
 */
@Composable
private fun DemoButton(
    text: String, hovered: Boolean, modifier: Modifier = Modifier
) {

    /*
     * Hover 時稍微放大。
     */
    val scale by animateFloatAsState(

        targetValue =

            if (hovered) {

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

                color =

                    if (hovered) {

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

                width =

                    if (hovered) {

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

            color =

                if (hovered) {

                    Color.Black

                } else {

                    Color.White
                },

            fontSize = 22.sp,

            fontWeight = FontWeight.Bold
        )
    }
}
