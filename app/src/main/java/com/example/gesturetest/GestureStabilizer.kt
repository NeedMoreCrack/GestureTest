package com.example.gesturetest.gesture


class GestureStabilizer(
    private val gestureConfirmCount: Int = 2,
    private val unknownConfirmCount: Int = 4
) {

    /*
     * 目前正式手勢。
     */
    private var stableGesture =
        "NO HAND"


    /*
     * 正在觀察的新手勢。
     */
    private var candidateGesture =
        ""


    /*
     * 候選手勢連續出現次數。
     */
    private var candidateCount =
        0


    fun stabilize(
        newGesture: String
    ): String {

        /*
         * 跟目前穩定結果相同。
         */
        if (
            newGesture ==
            stableGesture
        ) {
            clearCandidate()

            return stableGesture
        }


        /*
         * 出現新的候選手勢。
         */
        if (
            newGesture !=
            candidateGesture
        ) {
            candidateGesture =
                newGesture

            candidateCount =
                1

        } else {
            /*
             * 同一個候選結果再次出現。
             */
            candidateCount++
        }


        /*
         * UNKNOWN 比正常 gesture
         * 要求更多次確認。
         */
        val requiredCount =
            if (
                newGesture ==
                "UNKNOWN"
            ) {
                unknownConfirmCount
            } else {
                gestureConfirmCount
            }


        /*
         * 達到確認次數。
         */
        if (
            candidateCount >=
            requiredCount
        ) {
            stableGesture =
                newGesture

            clearCandidate()
        }


        return stableGesture
    }


    fun reset() {
        stableGesture =
            "NO HAND"

        clearCandidate()
    }


    private fun clearCandidate() {
        candidateGesture =
            ""

        candidateCount =
            0
    }
}
