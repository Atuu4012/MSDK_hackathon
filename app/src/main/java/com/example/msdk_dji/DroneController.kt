package com.example.msdk_dji

import android.util.Log
import dji.sdk.keyvalue.key.GimbalKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.gimbal.CtrlInfo
import dji.sdk.keyvalue.value.gimbal.GimbalSpeedRotation
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.KeyManager
import dji.v5.manager.aircraft.virtualstick.VirtualStickManager
import kotlin.math.abs

class DroneController {

    private val TAG = "DroneController"
    private var isVirtualStickEnabled = false

    // --- Paramètres ---
    private val GIMBAL_GAIN = 200.0
    private val DRONE_YAW_GAIN = 1000.0
    private val DEAD_ZONE_PERCENT = 0.05
    private val DRONE_ALIGNMENT_THRESHOLD_DEG = 10.0

    init {
        initVirtualStick()
    }

    /**
     * Centre la caméra sur la cible.
     * Si la caméra tourne trop, le drone suit.
     */
    fun centerOnDetection(detection: Detection, imageWidth: Int, imageHeight: Int) {
        // En mode centrage pur, on n'avance pas (vitesse = 0.0)
        trackAndApproach(detection, imageWidth, imageHeight, 0.0)
    }

    /**
     * Centre la cible ET avance vers elle.
     * Combine la logique de Gimbal et de Pitch Drone.
     */
    fun trackAndApproach(detection: Detection, imageWidth: Int, imageHeight: Int, forwardSpeedStick: Double) {
        if (!isVirtualStickEnabled) return

        // 1. Calculs visuels
        val imageCenterX = imageWidth / 2.0
        val imageCenterY = imageHeight / 2.0
        val bboxCenterX = detection.box.centerX().toDouble()
        val bboxCenterY = detection.box.centerY().toDouble()

        val errorX = (bboxCenterX - imageCenterX) / imageCenterX
        val errorY = (bboxCenterY - imageCenterY) / imageCenterY

        // 2. Commande Gimbal (Pitch & Yaw)
        var gimbalPitchSpeed = 0.0
        var gimbalYawSpeed = 0.0

        // Pitch (Haut/Bas)
        if (abs(errorY) > DEAD_ZONE_PERCENT) {
            gimbalPitchSpeed = -errorY * GIMBAL_GAIN
        }

        // Yaw Gimbal (Gauche/Droite)
        if (abs(errorX) > DEAD_ZONE_PERCENT) {
            gimbalYawSpeed = errorX * GIMBAL_GAIN
        }

        // Envoi Gimbal
        val rotation = GimbalSpeedRotation(gimbalPitchSpeed, 0.0, gimbalYawSpeed, CtrlInfo())
        KeyManager.getInstance().performAction(
            KeyTools.createKey(GimbalKey.KeyRotateBySpeed),
            rotation,
            null
        )

        // 3. Commande Drone : Alignement Yaw + Avancement Pitch

        // A. Calcul de l'alignement Yaw (Le drone suit le regard de la caméra)
        val gimbalYawRelative = KeyManager.getInstance().getValue(KeyTools.createKey(GimbalKey.KeyYawRelativeToAircraftHeading)) ?: 0.0
        var droneYawCommand = 0.0

        if (abs(gimbalYawRelative) > DRONE_ALIGNMENT_THRESHOLD_DEG) {
            val normalizedYaw = (gimbalYawRelative / 90.0).coerceIn(-1.0, 1.0)
            droneYawCommand = normalizedYaw * DRONE_YAW_GAIN
        }

        // B. Application des commandes aux sticks virtuels
        updateSticks(droneYawCommand, forwardSpeedStick)
    }

    /**
     * Met à jour les sticks virtuels (Yaw et Pitch)
     */
    private fun updateSticks(yawCommand: Double, pitchCommand: Double) {
        val leftStick = VirtualStickManager.getInstance().leftStick
        val rightStick = VirtualStickManager.getInstance().rightStick

        // Yaw (Rotation) sur le stick gauche
        leftStick.horizontalPosition = yawCommand.coerceIn(-660.0, 660.0).toInt()
        leftStick.verticalPosition = 0 // Pas de changement d'altitude

        // Pitch (Avancer/Reculer) sur le stick droit
        // Attention : Vérifiez le sens (souvent positif = avancer)
        rightStick.verticalPosition = pitchCommand.coerceIn(-660.0, 660.0).toInt()
        rightStick.horizontalPosition = 0 // Pas de Roll (pas chassés)
    }

    // Pour compatibilité interne, on garde cette méthode privée qui n'agit que sur le Yaw
    private fun updateDroneYaw(rawCommand: Double) {
        updateSticks(rawCommand, 0.0)
    }

    fun stopMovement() {
        if (!isVirtualStickEnabled) return

        updateSticks(0.0, 0.0)

        val stopRotation = GimbalSpeedRotation(0.0, 0.0, 0.0, CtrlInfo())
        KeyManager.getInstance().performAction(
            KeyTools.createKey(GimbalKey.KeyRotateBySpeed),
            stopRotation,
            null
        )
    }

    private fun initVirtualStick() {
        VirtualStickManager.getInstance().enableVirtualStick(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                isVirtualStickEnabled = true
                VirtualStickManager.getInstance().setVirtualStickAdvancedModeEnabled(false)
            }
            override fun onFailure(error: IDJIError) {
                isVirtualStickEnabled = false
                Log.e(TAG, "Erreur Virtual Stick: ${error.description()}")
            }
        })
    }

    /**
     * Mode Atterrissage de Précision (Gimbal à -90°).
     * Convertit les erreurs de l'image en déplacements latéraux (Roll/Pitch).
     * @param descentSpeed Vitesse de descente positive (ex: 0.5 m/s). Sera inversée en négatif pour le drone.
     */
    fun trackPrecisionLanding(detection: Detection, imageWidth: Int, imageHeight: Int, descentSpeedStick: Double) {
        if (!isVirtualStickEnabled) return

        val imageCenterX = imageWidth / 2.0
        val imageCenterY = imageHeight / 2.0
        val bboxCenterX = detection.box.centerX().toDouble()
        val bboxCenterY = detection.box.centerY().toDouble()

        // Calcul des erreurs (-1.0 à 1.0)
        val errorX = (bboxCenterX - imageCenterX) / imageCenterX
        val errorY = (bboxCenterY - imageCenterY) / imageCenterY

        // --- Commande Drone (Vue de dessus) ---
        // X image -> Roll Drone (Gauche/Droite)
        // Y image -> Pitch Drone (Avant/Arrière)
        // Note : Vérifier l'orientation de la caméra. Si la caméra est orientée "Nord",
        // Erreur Y positive (bas image) = Cible derrière le drone = Reculer.

        val rollGain = 200.0 // Gain agressif pour réagir vite
        val pitchGain = 200.0

        val rollCmd = (errorX * rollGain).coerceIn(-200.0, 200.0)
        // Inversion souvent nécessaire : si cible en haut de l'image (Y négatif), il faut avancer (Pitch positif)
        val pitchCmd = (-errorY * pitchGain).coerceIn(-200.0, 200.0)

        // --- Commande Verticale (Throttle) ---
        // On descend (valeur négative)
        val throttleCmd = -abs(descentSpeedStick).coerceIn(0.0, 200.0)

        val leftStick = VirtualStickManager.getInstance().leftStick
        val rightStick = VirtualStickManager.getInstance().rightStick

        leftStick.horizontalPosition = 0 // Pas de Yaw (on garde le cap)
        leftStick.verticalPosition = throttleCmd.toInt() // Descendre

        rightStick.horizontalPosition = rollCmd.toInt()  // Corriger X
        rightStick.verticalPosition = pitchCmd.toInt()   // Corriger Y
    }

    /**
     * Récupère le Pitch actuel du Gimbal pour savoir si on regarde le sol.
     * @return Angle en degrés (ex: -90.0 pour le sol, 0.0 pour l'horizon)
     */
    fun getGimbalPitch(): Double {
        return KeyManager.getInstance().getValue(KeyTools.createKey(GimbalKey.KeyPitchRelativeToAircraftHeading)) ?: 0.0
    }

    /**
     * Force le Gimbal à regarder vers le bas (-90°).
     */
    fun lookDown() {
        val rotation = dji.sdk.keyvalue.value.gimbal.GimbalAngleRotation(
            dji.sdk.keyvalue.value.gimbal.GimbalAngleRotationMode.ABSOLUTE_ANGLE,
            -90.0,
            null,
            null,
            CtrlInfo()
        )
        KeyManager.getInstance().performAction(
            KeyTools.createKey(GimbalKey.KeyRotateByAngle),
            rotation,
            null
        )
    }


    fun release() {
        stopMovement()
        VirtualStickManager.getInstance().disableVirtualStick(null)
    }
}
