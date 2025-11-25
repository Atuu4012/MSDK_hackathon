package com.example.msdk_dji

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import java.nio.FloatBuffer
import java.util.Collections
import androidx.core.graphics.scale

data class Detection(val box: RectF, val score: Float, val classIndex: Int)

class YoloDetector(context: Context, modelName: String) {
    private val env = OrtEnvironment.getEnvironment()
    private val session = env.createSession(readModelBytes(context, modelName))

    // Taille d'entrée du modèle (640x640 selon votre entrainement)
    private val INPUT_SIZE = 640

    fun close() {
        session.close()
        env.close()
    }

    fun detect(bitmap: Bitmap): Detection? {
        // 1. Redimensionner l'image
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false)

        // 2. Préparer le Tensor (Normalisation 0-255 -> 0-1)
        val floatBuffer = bitmapToFloatBuffer(resizedBitmap)
        val inputName = session.inputNames.iterator().next()
        val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        val inputTensor = OnnxTensor.createTensor(env, floatBuffer, shape)

        // 3. Inférence
        val result = session.run(Collections.singletonMap(inputName, inputTensor))
        val outputTensor = result[0] as OnnxTensor
        val outputData = outputTensor.floatBuffer.array() // Array plat [1 * 84 * 8400] (ex: 80 classes + 4 coords)

        // 4. Post-Processing simplifié (Trouver la box avec le meilleur score)
        // Structure YOLOv8 : [Batch, (cx, cy, w, h, class0, class1...), Anchors]
        // Note : On doit transposer ou itérer intelligemment.

        var bestScore = 0.0f
        var bestBox: RectF? = null
        var bestClass = -1

        // Dimensions du tenseur de sortie (dépend de votre modèle exact, ici standard v8)
        val numAnchors = 8400
        val numElements = outputData.size / numAnchors // ex: 4 coords + 7 classes = 11

        for (i in 0 until numAnchors) {
            // Trouver le score max parmi les classes pour cet anchor
            var maxClassScore = 0f
            var maxClassIdx = -1

            // Les 4 premiers sont x,y,w,h. Les classes commencent à l'index 4.
            for (c in 4 until numElements) {
                val score = outputData[c * numAnchors + i] // Accès transposé
                if (score > maxClassScore) {
                    maxClassScore = score
                    maxClassIdx = c - 4
                }
            }

            if (maxClassScore > 0.3f && maxClassScore > bestScore) { // Seuil de confiance
                bestScore = maxClassScore
                bestClass = maxClassIdx

                // Récupération des coords (Normalisées par rapport à 640)
                val cx = outputData[0 * numAnchors + i]
                val cy = outputData[1 * numAnchors + i]
                val w = outputData[2 * numAnchors + i]
                val h = outputData[3 * numAnchors + i]

                // Conversion en RectF (Coordonnées de l'image originale)
                val x1 = (cx - w / 2) * (bitmap.width.toFloat() / INPUT_SIZE)
                val y1 = (cy - h / 2) * (bitmap.height.toFloat() / INPUT_SIZE)
                val x2 = (cx + w / 2) * (bitmap.width.toFloat() / INPUT_SIZE)
                val y2 = (cy + h / 2) * (bitmap.height.toFloat() / INPUT_SIZE)

                bestBox = RectF(x1, y1, x2, y2)
            }
        }

        inputTensor.close()
        return if (bestBox != null) Detection(bestBox, bestScore, bestClass) else null
    }

    private fun bitmapToFloatBuffer(bitmap: Bitmap): FloatBuffer {
        val buffer = FloatBuffer.allocate(1 * 3 * INPUT_SIZE * INPUT_SIZE)
        buffer.rewind()
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (i in 0 until INPUT_SIZE * INPUT_SIZE) {
            val pixel = pixels[i]
            // Extraction RGB et Normalisation [0,1]
            buffer.put(i, ((pixel shr 16 and 0xFF) / 255.0f)) // R
            buffer.put(INPUT_SIZE * INPUT_SIZE + i, ((pixel shr 8 and 0xFF) / 255.0f)) // G
            buffer.put(2 * INPUT_SIZE * INPUT_SIZE + i, ((pixel and 0xFF) / 255.0f)) // B
        }
        return buffer
    }

    private fun readModelBytes(context: Context, fileName: String): ByteArray {
        return context.assets.open(fileName).readBytes()
    }
}