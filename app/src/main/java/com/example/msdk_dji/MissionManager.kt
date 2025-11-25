package com.example.msdk_dji

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
// Suppression des imports ActiveTrack problématiques
// import dji.sdk.keyvalue.key.CameraKey
// import dji.sdk.keyvalue.key.KeyTools
// import dji.sdk.keyvalue.value.common.ComponentIndexType
// import dji.v5.manager.KeyManager
import kotlin.math.abs

enum class MissionState {
    SEARCHING,      // Modèle 1 : Cherche le véhicule
    CENTERING,      // Modèle 1 : Centre le véhicule
    APPROACHING,    // Modèle 1 : Avance vers le véhicule
    INSPECTING,     // Modèle 2 : Cherche les défauts (Tracking manuel maintenu)
    FINISHED
}

class MissionManager(
    private val droneController: DroneController,
    private val laserManager: LaserRangingManager,
    private val vehicleDetector: YoloDetector,       // Modèle Véhicule
    private val vulnerabilityDetector: YoloDetector, // Modèle Défauts
    private val onOverlayUpdate: (List<RectF>, String) -> Unit
) {
    private val TAG = "MissionManager"

    var currentState = MissionState.SEARCHING
        private set

    private val TARGET_DISTANCE_METERS = 15.0f
    private val APPROACH_SPEED_STICK = 100.0 // Vitesse modérée

    fun processFrame(bitmap: Bitmap) {
        when (currentState) {
            // --- PHASE 1 : APPROCHE (Modèle Véhicule) ---
            MissionState.SEARCHING, MissionState.CENTERING, MissionState.APPROACHING -> {

                val detection = vehicleDetector.detect(bitmap)

                if (detection != null) {
                    onOverlayUpdate(listOf(detection.box), "VEHICLE")
                } else {
                    onOverlayUpdate(emptyList(), "")
                }

                handleVehicleLogic(detection, bitmap.width, bitmap.height)
            }

            // --- PHASE 2 : INSPECTION (Modèle Défauts) ---
            MissionState.INSPECTING -> {
                // ICI LEchangement : Au lieu de lancer ActiveTrack, on fait le tracking nous-même.
                // On a besoin de DEUX choses :
                // 1. Rester centré sur le véhicule (Besoin du modèle Véhicule pour le pilotage)
                // 2. Chercher les défauts (Besoin du modèle Défauts pour l'inspection)

                // Note : Si faire tourner les 2 modèles est trop lourd, on peut alterner 1 frame sur 2.
                // Ou alors, on suppose que le drone ne bouge pas trop et on ne corrige la position que si on perd la cible.

                // STRATÉGIE : On utilise le modèle Véhicule pour maintenir la position (Stationnaire face à la cible)
                val vehicleDetection = vehicleDetector.detect(bitmap)

                if (vehicleDetection != null) {
                    // On continue de centrer, mais avec une vitesse d'avance de 0.0
                    droneController.trackAndApproach(vehicleDetection, bitmap.width, bitmap.height, 0.0)
                } else {
                    // Si on perd le véhicule, on arrête tout par sécurité
                    droneController.stopMovement()
                }

                // EN PLUS : On lance la détection de défauts
                val flawDetection = vulnerabilityDetector.detect(bitmap)

                val boxes = if (flawDetection != null) listOf(flawDetection.box) else emptyList()
                onOverlayUpdate(boxes, "DEFECT")

                if (flawDetection != null) {
                    Log.d(TAG, "Défaut trouvé ! Score: ${flawDetection.score}")
                }
            }

            MissionState.FINISHED -> {
                droneController.stopMovement()
                onOverlayUpdate(emptyList(), "MISSION COMPLETE")
            }
        }
    }

    private fun handleVehicleLogic(detection: Detection?, width: Int, height: Int) {
        when (currentState) {
            MissionState.SEARCHING -> {
                if (detection != null) {
                    Log.i(TAG, "Véhicule trouvé -> CENTRAGE")
                    currentState = MissionState.CENTERING
                }
            }

            MissionState.CENTERING -> {
                if (detection != null) {
                    droneController.trackAndApproach(detection, width, height, 0.0)
                    if (isCentered(detection, width)) {
                        Log.i(TAG, "Véhicule centré -> APPROCHE")
                        currentState = MissionState.APPROACHING
                    }
                } else {
                    currentState = MissionState.SEARCHING
                }
            }

            MissionState.APPROACHING -> {
                val distance = laserManager.getCurrentCenterDistance()

                if (detection != null && distance != null) {
                    if (distance > TARGET_DISTANCE_METERS) {
                        droneController.trackAndApproach(detection, width, height, APPROACH_SPEED_STICK)
                    } else {
                        Log.i(TAG, "Distance atteinte ($distance m) -> INSPECTION")
                        droneController.stopMovement()
                        // On passe direct en inspection (plus besoin de 'STARTING_TRACK')
                        currentState = MissionState.INSPECTING
                    }
                } else {
                    droneController.stopMovement()
                }
            }
            else -> {}
        }
    }

    private fun isCentered(detection: Detection, imgWidth: Int): Boolean {
        val centerX = detection.box.centerX()
        val imgCenter = imgWidth / 2.0f
        return abs(centerX - imgCenter) / imgCenter < 0.15
    }

    fun stopMission() {
        droneController.stopMovement()
        currentState = MissionState.SEARCHING
    }
}
