package com.example.gesturetest

import kotlin.math.sqrt

class GestureInputController(
    private val smoothingAlpha: Float = 0.22f,
    private val pinchStartThreshold: Float = 0.45f,
    private val pinchReleaseThreshold: Float = 0.60f,
    private val noHandConfirmCount: Int = 4,
    private val mirrorX: Boolean = false
) {

    enum class GestureEvent {
        NONE, MOVE, PINCH_START, PINCH_HOLD, PINCH_RELEASE, NO_HAND
    }

    data class GestureInput(
        val cursorX: Float, val cursorY: Float,

        val cursorVisible: Boolean, val cursorControlEnabled: Boolean,

        val isPinching: Boolean,

        val event: GestureEvent,

        /*
         * 拇指 / 食指距離
         * 相對於手掌寬度。
         *
         * 越小代表 Pinch 越緊。
         */
        val pinchRatio: Float,

        /*
         * 0.0 ~ 1.0
         *
         * 代表 Pinch 完整程度。
         *
         * 例如：
         *
         * 0.90 = 90%
         */
        val pinchStrength: Float,

        val gestureName: String, val gestureConfidence: Float,

        val backHandOpen: Boolean,

        val rotationDegrees: Int
    )

    private enum class PinchState {
        OPEN, PINCHING
    }

    private data class Point2D(
        val x: Float, val y: Float
    )

    private var pinchState = PinchState.OPEN

    private var smoothX = 0.5f

    private var smoothY = 0.5f

    private var cursorInitialized = false

    private var noHandCount = 0

    private var lastResult = createNoHandResult()

    fun process(
        handResult: MediaPipeHandDetector.HandDetectionResult?
    ): GestureInput {

        /*
         * ==========================
         * No Hand
         * ==========================
         */

        if (handResult == null || handResult.landmarks.size < 21) {

            return processNoHand()
        }

        noHandCount = 0

        val landmarks = handResult.landmarks

        /*
         * 手背方向判斷。
         *
         * 現在 UI 開關已經不用它，
         * 但保留 Debug 方便後續使用。
         */
        val backHandOpen = isBackHandOpen(
            landmarks
        )

        /*
         * ==========================
         * Pinch
         * ==========================
         */

        val thumbTip = landmarks[4]

        val indexTip = landmarks[8]

        /*
         * 拇指尖 -> 食指尖
         */
        val thumbIndexDistance = distance(
            thumbTip, indexTip
        )

        /*
         * 手掌寬度：
         *
         * index MCP -> pinky MCP
         */
        val palmWidth = distance(
            landmarks[5], landmarks[17]
        )

        /*
         * 用手掌寬度 normalization，
         * 避免手離鏡頭遠近影響太大。
         */
        val pinchRatio = if (palmWidth > 0.0001f) {

            thumbIndexDistance / palmWidth

        } else {

            999f
        }

        /*
         * ==========================
         * Pinch Strength
         * ==========================
         *
         * pinchReleaseThreshold：
         * 視為 0%
         *
         * fullPinchRatio：
         * 視為 100%
         *
         * 目前 0.20 左右代表拇指、食指
         * 已經非常靠近。
         */
        val fullPinchRatio = 0.20f

        val pinchStrength =
            ((pinchReleaseThreshold - pinchRatio) / (pinchReleaseThreshold - fullPinchRatio)).coerceIn(
                0f, 1f
            )

        /*
         * ==========================
         * Pinch State Machine
         * ==========================
         */

        val event = when (pinchState) {

            PinchState.OPEN -> {

                if (pinchRatio <= pinchStartThreshold) {

                    pinchState = PinchState.PINCHING

                    GestureEvent.PINCH_START

                } else {

                    GestureEvent.MOVE
                }
            }

            PinchState.PINCHING -> {

                if (pinchRatio >= pinchReleaseThreshold) {

                    pinchState = PinchState.OPEN

                    GestureEvent.PINCH_RELEASE

                } else {

                    GestureEvent.PINCH_HOLD
                }
            }
        }

        /*
         * 目前只要有手，
         * Cursor 就可以控制。
         */
        val cursorControlEnabled = true

        /*
         * ==========================
         * Palm Center
         * ==========================
         *
         * 使用：
         *
         * index MCP
         * middle MCP
         * ring MCP
         * pinky MCP
         *
         * 平均值當 Cursor。
         */

        val palmIndexes = intArrayOf(
            5, 9, 13, 17
        )

        var rawX = 0f

        var rawY = 0f

        for (index in palmIndexes) {

            rawX += landmarks[index].x

            rawY += landmarks[index].y
        }

        rawX /= palmIndexes.size

        rawY /= palmIndexes.size

        /*
         * Camera rotation。
         */
        val rotatedPoint = rotateCoordinate(
            x = rawX, y = rawY, rotationDegrees = handResult.rotationDegrees
        )

        rawX = rotatedPoint.x

        rawY = rotatedPoint.y

        /*
         * 前鏡頭需要時可反轉 X。
         *
         * 現在使用後鏡頭，
         * mirrorX = false。
         */
        if (mirrorX) {

            rawX = 1f - rawX
        }

        rawX = rawX.coerceIn(
            0f, 1f
        )

        rawY = rawY.coerceIn(
            0f, 1f
        )

        /*
         * ==========================
         * Cursor EMA Smoothing
         * ==========================
         */

        if (!cursorInitialized) {

            smoothX = rawX

            smoothY = rawY

            cursorInitialized = true

        } else {

            smoothX = smoothX + smoothingAlpha * (rawX - smoothX)

            smoothY = smoothY + smoothingAlpha * (rawY - smoothY)
        }

        val result = GestureInput(
            cursorX = smoothX,

            cursorY = smoothY,

            cursorVisible = cursorInitialized,

            cursorControlEnabled = cursorControlEnabled,

            isPinching = pinchState == PinchState.PINCHING,

            event = event,

            pinchRatio = pinchRatio,

            pinchStrength = pinchStrength,

            gestureName = handResult.gestureName,

            gestureConfidence = handResult.gestureConfidence,

            backHandOpen = backHandOpen,

            rotationDegrees = handResult.rotationDegrees
        )

        lastResult = result

        return result
    }

    /*
     * ==========================
     * No Hand
     * ==========================
     */

    private fun processNoHand(): GestureInput {

        noHandCount++

        /*
         * MediaPipe 偶爾漏掉 1~3 幀，
         * 不要馬上把 Cursor 清掉。
         */
        if (noHandCount < noHandConfirmCount) {

            return lastResult.copy(
                event = GestureEvent.NONE
            )
        }

        pinchState = PinchState.OPEN

        cursorInitialized = false

        val result = createNoHandResult()

        lastResult = result

        return result
    }

    /*
     * ==========================
     * Coordinate Rotation
     * ==========================
     */

    private fun rotateCoordinate(
        x: Float, y: Float, rotationDegrees: Int
    ): Point2D {

        return when (rotationDegrees) {

            0 -> {

                Point2D(
                    x, y
                )
            }

            90 -> {

                Point2D(
                    1f - y, x
                )
            }

            180 -> {

                Point2D(
                    1f - x, 1f - y
                )
            }

            270 -> {

                Point2D(
                    y, 1f - x
                )
            }

            else -> {

                Point2D(
                    x, y
                )
            }
        }
    }

    /*
     * ==========================
     * Back Hand Detection
     * ==========================
     */

    private fun isBackHandOpen(
        landmarks: List<MediaPipeHandDetector.HandPoint>
    ): Boolean {

        val indexExtended = landmarks[8].y < landmarks[6].y

        val middleExtended = landmarks[12].y < landmarks[10].y

        val ringExtended = landmarks[16].y < landmarks[14].y

        val pinkyExtended = landmarks[20].y < landmarks[18].y

        val thumbOpen = distance(
            landmarks[4], landmarks[5]
        ) > distance(
            landmarks[3], landmarks[5]
        )

        val wrist = landmarks[0]

        val indexMcp = landmarks[5]

        val pinkyMcp = landmarks[17]

        val ax = indexMcp.x - wrist.x

        val ay = indexMcp.y - wrist.y

        val bx = pinkyMcp.x - wrist.x

        val by = pinkyMcp.y - wrist.y

        val cross = ax * by - ay * bx

        /*
         * 如果之後發現正反方向相反，
         * 將 > 改成 < 即可。
         */
        val backHandFacingCamera = cross > 0f

        return (indexExtended && middleExtended && ringExtended && pinkyExtended && thumbOpen && backHandFacingCamera)
    }

    /*
     * ==========================
     * Distance
     * ==========================
     */

    private fun distance(
        a: MediaPipeHandDetector.HandPoint, b: MediaPipeHandDetector.HandPoint
    ): Float {

        val dx = a.x - b.x

        val dy = a.y - b.y

        return sqrt(
            dx * dx + dy * dy
        )
    }

    /*
     * ==========================
     * Default Input
     * ==========================
     */

    private fun createNoHandResult(): GestureInput {

        return GestureInput(
            cursorX = 0.5f,

            cursorY = 0.5f,

            cursorVisible = false,

            cursorControlEnabled = false,

            isPinching = false,

            event = GestureEvent.NO_HAND,

            pinchRatio = 1f,

            pinchStrength = 0f,

            gestureName = "NO HAND",

            gestureConfidence = 0f,

            backHandOpen = false,

            rotationDegrees = 0
        )
    }

    fun reset() {

        pinchState = PinchState.OPEN

        noHandCount = 0

        cursorInitialized = false

        smoothX = 0.5f

        smoothY = 0.5f

        lastResult = createNoHandResult()
    }
}
