package com.example.msdk_dji

import android.util.Log
import dji.sdk.keyvalue.key.CameraKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.manager.KeyManager

class LaserRangingManager {

    private val TAG = "LaserRangingManager"
    // Pas de setup d'évitement nécessaire ici, on laisse le drone gérer ses capteurs par défaut.

    /**
     * Lit la distance mesurée par le télémètre laser (LRF) de la caméra.
     * Cette méthode est non bloquante et retourne la dernière valeur connue.
     */
    fun getCurrentCenterDistance(): Float? {
        try {
            // Création de la clé pour le module Laser
            // ComponentIndexType.LEFT_OR_MAIN est standard pour la caméra principale (ex: H20, M30)
            val laserKey = KeyTools.createKey(
                CameraKey.KeyLaserMeasureInformation,
                ComponentIndexType.LEFT_OR_MAIN
            )
            val laserInfo = KeyManager.getInstance().getValue(laserKey)

            if (laserInfo != null && laserInfo.distance > 0) {
                // La distance est souvent en mètres (Float ou Double)
                return laserInfo.distance.toFloat()
            }
        } catch (e: Exception) {
            // Log silencieux pour ne pas spammer si le laser est éteint
            Log.w(TAG, "Erreur lecture Laser: ${e.message}")
        }
        return null
    }

    fun release() {
        // Rien à libérer
    }
}
