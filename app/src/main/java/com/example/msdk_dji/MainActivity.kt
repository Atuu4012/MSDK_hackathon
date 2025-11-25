package com.example.msdk_dji

import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    // 1. Instancier le moteur
    private val trackingEngine = SmartTrackingEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ... setup UI ...

        setupButtons()
        setupAIProcessing()
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            // Commencer à tracker en maintenant 20 mètres de distance
            trackingEngine.startTracking(20.0f)
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            trackingEngine.stopTracking()
        }
    }

    /**
     * Simulation de votre callback IA (YOLO, TFLite, Google ML Kit...)
     */
    private fun setupAIProcessing() {
        // Imaginer que ceci est appelé par votre analyseur d'image à chaque frame
        // myAiDetector.setCallback { result, w, h ->
            // C'est ici que la magie opère :
            // On passe juste le résultat au moteur, il gère tout le reste.
            // trackingEngine.onNewDetectionResult(result, w, h)
        // }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Très important pour libérer les sticks virtuels
        trackingEngine.destroy()
    }
}