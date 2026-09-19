package com.example.gesturetest

/**
 * 專門處理 UI 開啟 / 關閉的手勢順序。
 *
 * OPEN:
 * Closed_Fist -> Open_Palm
 *
 * CLOSE:
 * Open_Palm -> Closed_Fist
 *
 * 每一個 Gesture 必須連續確認指定幀數，
 * 才會被視為 Stable Gesture。
 */
class UiGestureController(
    private val requiredConfirmFrames: Int = 2,
    private val minGestureConfidence: Float = 0.65f
) {

    enum class UiEvent {
        NONE,
        OPEN_UI,
        CLOSE_UI
    }

    /*
     * 目前正在累積確認的 Gesture。
     */
    private var candidateGesture = ""

    /*
     * candidateGesture 已連續出現幾次。
     */
    private var candidateCount = 0

    /*
     * 上一個真正確認完成的 Gesture。
     *
     * 例如：
     *
     * Closed_Fist
     * ->
     * Open_Palm
     *
     * 才會形成 OPEN transition。
     */
    private var previousStableGesture = ""

    /**
     * 每收到一次 MediaPipe Gesture Result
     * 就呼叫一次。
     */
    fun process(
        gestureName: String,
        gestureConfidence: Float,
        uiVisible: Boolean
    ): UiEvent {

        /*
         * 只處理我們需要的兩種 Gesture。
         */
        val currentGesture =
            when {

                gestureConfidence < minGestureConfidence -> {
                    null
                }

                gestureName == "Closed_Fist" -> {
                    "Closed_Fist"
                }

                gestureName == "Open_Palm" -> {
                    "Open_Palm"
                }

                else -> {
                    null
                }
            }

        /*
         * UNKNOWN / None / Thumb_Up...
         *
         * 不清除 previousStableGesture。
         *
         * 因為使用者從：
         *
         * ✊ -> ✋
         *
         * 的過程中很容易有幾幀 UNKNOWN。
         */
        if (currentGesture == null) {

            candidateGesture = ""
            candidateCount = 0

            return UiEvent.NONE
        }

        /*
         * ==========================
         * Stable Gesture 確認
         * ==========================
         */

        if (currentGesture == candidateGesture) {

            candidateCount++

        } else {

            candidateGesture = currentGesture
            candidateCount = 1
        }

        /*
         * 還沒連續確認足夠次數。
         */
        if (candidateCount < requiredConfirmFrames) {

            return UiEvent.NONE
        }

        /*
         * 這個 Gesture 已經穩定。
         */
        candidateGesture = ""
        candidateCount = 0

        /*
         * 如果跟上一個 Stable Gesture 一樣，
         * 不需要重複處理。
         *
         * 例如：
         *
         * Closed_Fist
         * Closed_Fist
         * Closed_Fist
         *
         * 不應該一直產生 transition。
         */
        if (currentGesture == previousStableGesture) {

            return UiEvent.NONE
        }

        val oldGesture =
            previousStableGesture

        /*
         * 更新 Stable Gesture。
         */
        previousStableGesture =
            currentGesture

        /*
         * ==========================
         * OPEN UI
         *
         * ✊ -> ✋
         * ==========================
         */
        if (
            !uiVisible &&
            oldGesture == "Closed_Fist" &&
            currentGesture == "Open_Palm"
        ) {

            return UiEvent.OPEN_UI
        }

        /*
         * ==========================
         * CLOSE UI
         *
         * ✋ -> ✊
         * ==========================
         */
        if (
            uiVisible &&
            oldGesture == "Open_Palm" &&
            currentGesture == "Closed_Fist"
        ) {

            return UiEvent.CLOSE_UI
        }

        return UiEvent.NONE
    }

    fun getPreviousStableGesture(): String {

        return if (previousStableGesture.isBlank()) {
            "NONE"
        } else {
            previousStableGesture
        }
    }

    fun getCandidateGesture(): String {

        return if (candidateGesture.isBlank()) {
            "NONE"
        } else {
            candidateGesture
        }
    }


    fun getCandidateProgress(): String {

        return if (candidateGesture.isBlank()) {
            "0/$requiredConfirmFrames"
        } else {
            "$candidateCount/$requiredConfirmFrames"
        }
    }

    fun reset() {

        candidateGesture = ""
        candidateCount = 0
        previousStableGesture = ""
    }
}
