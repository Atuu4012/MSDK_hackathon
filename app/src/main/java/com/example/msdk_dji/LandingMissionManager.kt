package com.example.msdk_dji

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import kotlin.math.abs

enum class LandingState {
    SEARCHING,      // Cherche la piste
    APPROACHING,    // Approche classique (Vue avant)
    ALIGNING_DOWN,  // Transition : On bascule la caméra vers le bas
    VERTICAL_TRACK, // Suivi de la piste mouvante (Stationnaire ou synchronisé)
    DESCENDING,     // Descente contrôlée
    LANDED          // Fin
}

class LandingMissionManager(
    private val droneController: DroneController,
    private val laserManager: LaserRangingManager,
    private val landingPadDetector: YoloDetector, // Modèle "landing_pad.onnx"
    private val onOverlayUpdate: (List<RectF>, String) -> Unit
) {
    private val TAG = "LandingManager"
    var currentState = LandingState.SEARCHING
        private set

    // Paramètres
    private val SWITCH_ANGLE_THRESHOLD = -80.0 // Degrés. Si gimbal < -80, on considère qu'on est au-dessus.
    private val HOVER_HEIGHT = 3.0f // Mètres. Hauteur de synchro avant atterrissage.
    private val LANDING_SPEED = 100.0 // Vitesse lente pour la descente

    fun processFrame(bitmap: Bitmap) {
        val detection = landingPadDetector.detect(bitmap)

        // Overlay visuel
        if (detection != null) {
            onOverlayUpdate(listOf(detection.box), "H: ${String.format("%.1f", laserManager.getCurrentCenterDistance() ?: 0f)}m")
        } else {
            onOverlayUpdate(emptyList(), "NO TARGET")
        }

        when (currentState) {
            LandingState.SEARCHING -> {
                if (detection != null) {
                    currentState = LandingState.APPROACHING
                }
            }

            LandingState.APPROACHING -> {
                if (detection != null) {
                    // On utilise la méthode classique : Gimbal suit la cible, Drone avance
                    // Plus on avance, plus le Gimbal va regarder vers le bas naturellement
                    droneController.trackAndApproach(detection, bitmap.width, bitmap.height, 200.0) // Vitesse 200

                    // Vérification de l'angle du Gimbal
                    val gimbalPitch = droneController.getGimbalPitch()
                    if (gimbalPitch < SWITCH_ANGLE_THRESHOLD) {
                        Log.i(TAG, "Verticale atteinte (Angle: $gimbalPitch). Bascule en mode Vertical.")
                        currentState = LandingState.ALIGNING_DOWN
                    }
                } else {
                    droneController.stopMovement() // Sécurité
                }
            }

            LandingState.ALIGNING_DOWN -> {
                // On force le Gimbal à -90° parfait pour être sûr
                droneController.lookDown()
                // Petite pause ou vérification immédiate
                currentState = LandingState.VERTICAL_TRACK
            }

            LandingState.VERTICAL_TRACK -> {
                if (detection != null) {
                    // On suit la cible (Roll/Pitch) SANS descendre (Speed = 0)
                    // Cela permet de se synchroniser avec la piste mouvante
                    droneController.trackPrecisionLanding(detection, bitmap.width, bitmap.height, 0.0)

                    // Vérification Altitude (Laser ou Baromètre)
                    val altitude = laserManager.getCurrentCenterDistance() ?: 100f

                    // Si on est bien aligné (erreur faible), on descend
                    if (isAligned(detection, bitmap.width)) {
                        Log.i(TAG, "Aligné & Synchro. Début descente.")
                        currentState = LandingState.DESCENDING
                    }
                } else {
                    // Si on perd la cible, on remonte ou on s'arrête ?
                    // droneController.ascend() // Optionnel
                    droneController.stopMovement()
                }
            }

            LandingState.DESCENDING -> {
                if (detection != null) {
                    // On continue de corriger la position latérale tout en descendant
                    droneController.trackPrecisionLanding(detection, bitmap.width, bitmap.height, LANDING_SPEED)

                    val altitude = laserManager.getCurrentCenterDistance()

                    // Condition d'atterrissage (ex: < 30cm)
                    if (altitude != null && altitude < 0.3f) {
                        Log.i(TAG, "Touchdown imminent. Arrêt moteurs.")
                        // Déclencher l'auto-landing final du drone ou couper les moteurs
                        performFinalLanding()
                        currentState = LandingState.LANDED
                    }
                }
            }

            LandingState.LANDED -> {
                droneController.stopMovement()
            }
        }
    }

    private fun isAligned(detection: Detection, width: Int): Boolean {
        val error = abs(detection.box.centerX() - width / 2.0)
        return error < (width * 0.1) // Centré à 10%
    }

    private fun performFinalLanding() {
        // Utiliser la commande d'atterrissage auto DJI
        KeyManager.getInstance().performAction(
            KeyTools.createKey(dji.sdk.keyvalue.key.FlightControllerKey.KeyStartAutoLanding),
            null, null
        )
    }

    fun stopMission() {
        droneController.stopMovement()
        currentState = LandingState.SEARCHING
    }
}
