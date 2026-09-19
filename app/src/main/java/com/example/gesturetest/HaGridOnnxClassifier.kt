package com.example.gesturetest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

class HaGridOnnxClassifier(
    context: Context
) : AutoCloseable {

    private val ortEnvironment: OrtEnvironment = OrtEnvironment.getEnvironment()

    private val ortSession: OrtSession

    companion object {

        private const val TAG = "HaGridONNX"

        private const val MODEL_FILE = "MobileNetV3_large.onnx"

        private const val INPUT_SIZE = 224

        /*
         * HaGRID MobileNetV3_large 訓練設定
         */
        private val MEAN = floatArrayOf(
            0.54f, 0.499f, 0.474f
        )

        private val STD = floatArrayOf(
            0.234f, 0.235f, 0.231f
        )

        /*
         * output[0] ~ output[33]
         *
         * 順序一定不能改。
         */
        val LABELS = listOf(
            "grabbing",
            "grip",
            "holy",
            "point",
            "call",
            "three3",
            "timeout",
            "xsign",
            "hand_heart",
            "hand_heart2",
            "little_finger",
            "middle_finger",
            "take_picture",
            "dislike",
            "fist",
            "four",
            "like",
            "mute",
            "ok",
            "one",
            "palm",
            "peace",
            "peace_inverted",
            "rock",
            "stop",
            "stop_inverted",
            "three",
            "three2",
            "two_up",
            "two_up_inverted",
            "three_gun",
            "thumb_index",
            "thumb_index2",
            "no_gesture"
        )
    }

    init {

        val modelBytes = context.assets.open(MODEL_FILE).use {
                it.readBytes()
            }

        val sessionOptions = OrtSession.SessionOptions()

        ortSession = ortEnvironment.createSession(
            modelBytes, sessionOptions
        )

        Log.d(
            TAG, "ONNX session created"
        )

        Log.d(
            TAG, "Input names = ${ortSession.inputNames}"
        )

        Log.d(
            TAG, "Output names = ${ortSession.outputNames}"
        )
    }

    data class Result(
        val label: String, val confidence: Float, val classIndex: Int
    )

    fun classify(
        bitmap: Bitmap
    ): Result {

        /*
         * HaGRID 不是直接暴力 resize 成 224x224。
         *
         * 官方 preprocessing：
         *
         * LongestMaxSize(224)
         * ↓
         * PadIfNeeded(224x224)
         */
        val processedBitmap = resizeAndPad(bitmap)

        val inputData = bitmapToFloatArray(
            processedBitmap
        )

        /*
         * ONNX input:
         *
         * [1, 3, 224, 224]
         */
        val inputShape = longArrayOf(
            1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()
        )

        val inputTensor = OnnxTensor.createTensor(
            ortEnvironment, FloatBuffer.wrap(inputData), inputShape
        )

        inputTensor.use { tensor ->

            val inputName = ortSession.inputNames.first()

            ortSession.run(
                mapOf(
                    inputName to tensor
                )
            ).use { results ->

                @Suppress("UNCHECKED_CAST") val output = results[0].value as Array<FloatArray>

                val logits = output[0]

                /*
                 * 模型輸出是 logits。
                 *
                 * 用 softmax 轉成比較直覺的 confidence。
                 */
                val probabilities = softmax(logits)

                var bestIndex = 0
                var bestScore = probabilities[0]

                for (i in 1 until probabilities.size) {

                    if (probabilities[i] > bestScore) {

                        bestScore = probabilities[i]

                        bestIndex = i
                    }
                }

                return Result(
                    label = LABELS[bestIndex],

                    confidence = bestScore,

                    classIndex = bestIndex
                )
            }
        }
    }

    /**
     * HaGRID：
     *
     * LongestMaxSize 224
     * +
     * PadIfNeeded 224x224
     *
     * padding RGB = [144,144,144]
     */
    private fun resizeAndPad(
        source: Bitmap
    ): Bitmap {

        val width = source.width

        val height = source.height

        val scale = INPUT_SIZE.toFloat() / max(width, height)

        val targetWidth = (width * scale).roundToInt()

        val targetHeight = (height * scale).roundToInt()

        val resized = Bitmap.createScaledBitmap(
            source, targetWidth, targetHeight, true
        )

        val result = Bitmap.createBitmap(
            INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(result)

        /*
         * HaGRID 官方 padding：
         *
         * [144,144,144]
         */
        canvas.drawColor(
            Color.rgb(
                144, 144, 144
            )
        )

        val left = (INPUT_SIZE - targetWidth) / 2f

        val top = (INPUT_SIZE - targetHeight) / 2f

        canvas.drawBitmap(
            resized, left, top, null
        )

        if (resized !== source) {
            resized.recycle()
        }

        return result
    }

    /**
     * Bitmap:
     *
     * HWC RGB
     *
     * ↓
     *
     * ONNX:
     *
     * NCHW
     * [1,3,224,224]
     */
    private fun bitmapToFloatArray(
        bitmap: Bitmap
    ): FloatArray {

        val pixels = IntArray(
            INPUT_SIZE * INPUT_SIZE
        )

        bitmap.getPixels(
            pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE
        )

        val channelSize = INPUT_SIZE * INPUT_SIZE

        val result = FloatArray(
            3 * channelSize
        )

        for (i in pixels.indices) {

            val pixel = pixels[i]

            val red = Color.red(pixel) / 255f

            val green = Color.green(pixel) / 255f

            val blue = Color.blue(pixel) / 255f

            /*
             * Normalize：
             *
             * (pixel - mean) / std
             */

            result[i] = (red - MEAN[0]) / STD[0]

            result[channelSize + i] = (green - MEAN[1]) / STD[1]

            result[channelSize * 2 + i] = (blue - MEAN[2]) / STD[2]
        }

        return result
    }

    private fun softmax(
        logits: FloatArray
    ): FloatArray {

        /*
         * 防止 exp overflow
         */
        val maxValue = logits.maxOrNull() ?: 0f

        val expValues = FloatArray(
            logits.size
        )

        var sum = 0.0

        for (i in logits.indices) {

            val value = exp(
                (logits[i] - maxValue).toDouble()
            )

            expValues[i] = value.toFloat()

            sum += value
        }

        for (i in expValues.indices) {

            expValues[i] = (expValues[i] / sum).toFloat()
        }

        return expValues
    }

    override fun close() {

        ortSession.close()

        /*
         * OrtEnvironment 通常可以全 App 共用。
         * 這裡不一定要手動 close environment。
         */

        Log.d(
            TAG, "ONNX session closed"
        )
    }
}
