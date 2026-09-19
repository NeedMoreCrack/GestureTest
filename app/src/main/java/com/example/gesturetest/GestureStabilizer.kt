package com.example.gesturetest.gesture

class GestureStabilizer(
    private val gestureConfirmCount: Int = 2, private val unknownConfirmCount: Int = 4
) {

    data class StableResult(
        val label: String, val confidence: Float
    )

    /*
     * 目前正式顯示的結果。
     */
    private var stableGesture = "NO HAND"

    private var stableConfidence = 0f


    /*
     * 正在觀察的新手勢。
     */
    private var candidateGesture = ""

    private var candidateConfidence = 0f

    private var candidateCount = 0


    fun stabilize(
        newGesture: String, confidence: Float
    ): StableResult {

        /*
         * 跟目前穩定結果相同。
         *
         * 手勢沒變時允許更新 confidence。
         */
        if (newGesture == stableGesture) {

            clearCandidate()

            stableConfidence = confidence

            return StableResult(
                label = stableGesture, confidence = stableConfidence
            )
        }


        /*
         * 出現新的候選手勢。
         */
        if (newGesture != candidateGesture) {

            candidateGesture = newGesture

            candidateConfidence = confidence

            candidateCount = 1

        } else {

            /*
             * 同一候選結果再次出現。
             */
            candidateCount++

            candidateConfidence = confidence
        }


        /*
         * UNKNOWN 要比正常 gesture
         * 更難進入穩定狀態。
         */
        val requiredCount = if (newGesture == "UNKNOWN") {
            unknownConfirmCount
        } else {
            gestureConfirmCount
        }


        /*
         * 達到確認次數才真正切換。
         */
        if (candidateCount >= requiredCount) {

            stableGesture = candidateGesture

            stableConfidence = candidateConfidence

            clearCandidate()
        }


        return StableResult(
            label = stableGesture, confidence = stableConfidence
        )
    }


    fun reset(): StableResult {

        stableGesture = "NO HAND"

        stableConfidence = 0f

        clearCandidate()

        return StableResult(
            label = stableGesture, confidence = stableConfidence
        )
    }


    private fun clearCandidate() {

        candidateGesture = ""

        candidateConfidence = 0f

        candidateCount = 0
    }
}
