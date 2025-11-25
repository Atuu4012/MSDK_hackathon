package com.example.msdk_dji

import android.graphics.RectF
import android.util.Log

// --- IMPORTS CORRIGÉS POUR MSDK 5.17.0 ---
import dji.sdk.key.CameraKey
import dji.sdk.key.GimbalKey
import dji.sdk.key.value.camera.LaserRangingInformation
import dji.sdk.key.value.gimbal.GimbalSpeedRotation // C'est la bonne classe !
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.KeyManager
import dji.v5.manager.KeyTools
// -----------------------------------------

class DroneController {

    private val Kp = 0.15
    private val DeadZone = 50

    fun trackAndMeasure(box: RectF, imgW: Int, imgH: Int) {
        val centerX = imgW / 2.0
        val centerY = imgH / 2.0
        val errorX = box.centerX() - centerX
        val errorY = box.centerY() - centerY

        if (Math.abs(errorX) < DeadZone && Math.abs(errorY) < DeadZone) {
            stopGimbal()
            triggerLaser()
        } else {
            // Inversion possible selon le drone, à tester
            val yawSpeed = errorX * Kp
            val pitchSpeed = errorY * Kp
            moveGimbal(pitchSpeed, yawSpeed)
        }
    }

    private fun moveGimbal(pitch: Double, yaw: Double) {
        // CORRECTION : Utilisation de GimbalSpeedRotation pour la clé KeyRotateBySpeed
        val rotation = GimbalSpeedRotation()
        rotation.pitch = pitch
        rotation.yaw = yaw
        rotation.roll = 0.0
        // CtrlFlag : bitmask pour dire qu'on contrôle Pitch et Yaw
        // 1 = Pitch, 2 = Roll, 4 = Yaw. Donc 1+4 = 5.
        // Mais le SDK gère souvent ça auto si on set les valeurs.

        KeyManager.getInstance().performAction(
            KeyTools.createKey(GimbalKey.KeyRotateBySpeed),
            rotation,
            null
        )
    }

    private fun stopGimbal() {
        moveGimbal(0.0, 0.0)
    }

    private fun triggerLaser() {
        // 1. Activer le module
        KeyManager.getInstance().setValue(
            KeyTools.createKey(CameraKey.KeyLaserRangeFinderEnabled),
            true,
            null
        )

        // 2. Lire la valeur
        KeyManager.getInstance().getValue(
            KeyTools.createKey(CameraKey.KeyLaserRangingInformation),
            object : CommonCallbacks.CompletionCallbackWithParam<LaserRangingInformation> {
                override fun onSuccess(info: LaserRangingInformation?) {
                    if (info != null) {
                        // Dans la v5.17, c'est parfois 'distance' tout court ou via getter
                        val dist = info.distance
                        Log.d("MISSION", "Distance Laser: $dist m")

                        val target = info.targetLocation
                        if (target != null) {
                            Log.d("MISSION", "GPS Cible: ${target.latitude}, ${target.longitude}")
                        }
                    }
                }
                override fun onFailure(e: IDJIError) {
                    Log.e("MISSION", "Erreur Laser: ${e.description()}")
                }
            }
        )
    }
}