package com.example.msdk_dji

import kotlin.math.abs

class VisualServoingTracker(private val droneController: DroneController) {

    // --- Configuration (Gains & Seuils) ---
    private val GIMBAL_GAIN = 100.0
    private val DRONE_YAW_GAIN = 500.0
    private val DRONE_PITCH_GAIN = 1      // 1m d'erreur = 1.5 m/s

    private val VISUAL_DEAD_ZONE = 0.05     // 5% du cadre
    private val YAW_ALIGN_THRESHOLD = 5.0   // 5 degrés
    private val DISTANCE_TOLERANCE_M = 1.5  // Zone de confort de 1.5m

    // Vitesse Max Drone (pour la sécurité) - map approximatif vers stick 660
    private val MAX_STICK_VALUE = 500.0
    private val MAX_SPEED_MS : Float = 4.0F // Correspondance approx 660 stick = 15 m/s

    /**
     * Calcule et applique les commandes de mouvement.
     * À appeler à chaque nouvelle frame vidéo ou tick de détection.
     */
    fun update(
        detection: DetectionResult,
        laserDistance: Float?,
        targetDistance: Float,
        imageWidth: Int,
        imageHeight: Int
    ) {
        // 1. Calculer les commandes Gimbal (Vision)
        val (gimbalPitch, gimbalYaw) = calculateGimbalCommand(detection, imageWidth, imageHeight)
        droneController.sendGimbalSpeed(gimbalPitch, gimbalYaw)

        // 2. Calculer la commande de Rotation Drone (Alignement)
        val droneYaw = calculateDroneAlignmentCommand()

        // 3. Calculer la commande d'Avancement Drone (Laser)
        val dronePitch = calculateDistanceCommand(laserDistance, targetDistance)

        // 4. Appliquer au Drone
        droneController.sendVirtualStick(droneYaw, dronePitch)
    }

    private fun calculateGimbalCommand(detection: DetectionResult, w: Int, h: Int): Pair<Double, Double> {
        val centerX = w / 2.0
        val centerY = h / 2.0

        // Erreur normalisée (-1.0 à 1.0)
        val errorX = (detection.boundingBox.centerX() - centerX) / centerX
        val errorY = (detection.boundingBox.centerY() - centerY) / centerY

        var pitchSpeed = 0.0
        var yawSpeed = 0.0

        if (abs(errorY) > VISUAL_DEAD_ZONE) pitchSpeed = -errorY * GIMBAL_GAIN
        if (abs(errorX) > VISUAL_DEAD_ZONE) yawSpeed = errorX * GIMBAL_GAIN

        return Pair(pitchSpeed, yawSpeed)
    }

    private fun calculateDroneAlignmentCommand(): Double {
        val relativeYaw = droneController.getGimbalYawRelativeToAircraft()

        if (abs(relativeYaw) > YAW_ALIGN_THRESHOLD) {
            val normalized = (relativeYaw / 90.0).coerceIn(-1.0, 1.0)
            return normalized * DRONE_YAW_GAIN
        }
        return 0.0
    }

    private fun calculateDistanceCommand(currentDist: Float?, targetDist: Float): Double {
        if (currentDist == null || currentDist <= 0) {
            // Sécurité : Pas de laser = Pas d'avancement
            return 0.0
        }

        val error = currentDist - targetDist

        if (abs(error) > DISTANCE_TOLERANCE_M) {
            // Conversion Erreur (mètres) -> Vitesse (m/s)
            val speedMs = (error * DRONE_PITCH_GAIN).coerceIn(-MAX_SPEED_MS, MAX_SPEED_MS)

            // Conversion Vitesse (m/s) -> Stick (0-660)
            // Produit en croix simple
            return speedMs * (MAX_STICK_VALUE / MAX_SPEED_MS)
        }

        return 0.0
    }
}
