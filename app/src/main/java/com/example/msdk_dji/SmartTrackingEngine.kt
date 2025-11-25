package com.example.msdk_dji

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Orchestrateur principal.
 * Il reçoit les détections de l'IA et pilote les sous-systèmes.
 */
class SmartTrackingEngine {

    private val TAG = "SmartTrackingEngine"

    // --- Sous-systèmes ---
    private val droneController = DroneController()
    private val laserManager = LaserRangingManager()
    private val tracker = VisualServoingTracker(droneController)

    // --- État ---
    private var isTrackingActive = false
    private var targetDistanceMeters = 15.0f // Distance par défaut

    // --- Sécurité (Watchdog) ---
    // Si on ne reçoit pas de détection pendant ce délai, on arrête tout.
    private val WATCHDOG_TIMEOUT_MS = 500L
    private val watchdogHandler = Handler(Looper.getMainLooper())
    private val watchdogRunnable = Runnable {
        if (isTrackingActive) {
            Log.w(TAG, "ALERTE: Perte de flux vidéo ou détection trop lente -> Arrêt d'urgence")
            stopTracking()
        }
    }
    /**
     * Active le mode poursuite.
     * @param distanceMeters Distance cible à maintenir avec le véhicule.
     */
    fun startTracking(distanceMeters: Float) {
        isTrackingActive = true
        targetDistanceMeters = distanceMeters
        Log.i(TAG, "Tracking démarré. Distance cible: ${targetDistanceMeters}m")
    }
    /**
     * Désactive le mode poursuite et immobilise le drone.
     */
    fun stopTracking() {
        isTrackingActive = false
        droneController.stopAll()
        Log.i(TAG, "Tracking arrêté.")
    }
    /**
     * POINT D'ENTRÉE PRINCIPAL
     * Cette méthode doit être appelée à chaque fois que votre IA analyse une image.
     * Idéalement 15 à 30 fois par seconde.
     *
     * @param result Le résultat de votre détection (ou null si rien trouvé)
     * @param width Largeur de l'image analysée
     * @param height Hauteur de l'image analysée
     */
    fun onNewDetectionResult(result: DetectionResult?, width: Int, height: Int) {
        // 1. Reset du Watchdog de sécurité (on a reçu un signe de vie)
        resetWatchdog()
        if (!isTrackingActive) {
            return
        }

        if (result != null) {
            // A. Cible trouvée -> On récupère la distance Laser
            val currentDistance = laserManager.getCurrentCenterDistance()

            // B. On met à jour l'asservissement
            tracker.update(
                detection = result,
                laserDistance = currentDistance,
                targetDistance = targetDistanceMeters,
                imageWidth = width,
                imageHeight = height
            )
        } else {
            // C. Pas de cible sur cette frame -> On arrête le mouvement par sécurité
            // (On pourrait implémenter une logique de "recherche" ici, mais stop est plus sûr)
            droneController.stopAll()
        }
    }
    /**
     * Nettoyage final (à appeler dans le onDestroy de l'Activity)
     */
    fun destroy() {
        stopTracking()
        watchdogHandler.removeCallbacksAndMessages(null)
        droneController.release()
        laserManager.release()
    }
    // --- Gestion Watchdog ---
    private fun resetWatchdog() {
        watchdogHandler.removeCallbacks(watchdogRunnable)
        // Si pas de nouvelle frame dans 500ms, on déclenche l'arrêt
        watchdogHandler.postDelayed(watchdogRunnable, WATCHDOG_TIMEOUT_MS)
    }
}
