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

class DroneController {
    private val TAG = "DroneController"
    private var isVirtualStickEnabled = false

    init {
        initVirtualStick()
    }
    /**
     * Envoie une commande de vitesse brute au Gimbal.
     */
    fun sendGimbalSpeed(pitchSpeed: Double, yawSpeed: Double) {
        val rotation = GimbalSpeedRotation(pitchSpeed, 0.0, yawSpeed, CtrlInfo())
        KeyManager.getInstance().performAction(
            KeyTools.createKey(GimbalKey.KeyRotateBySpeed),
            rotation,
            null
        )
    }

    /**
     * Envoie une commande aux sticks du drone.
     * @param yaw Rotation (-660 à 660)
     * @param pitch Avancer/Reculer (-660 à 660)
     */
    fun sendVirtualStick(yaw: Double, pitch: Double) {
        if (!isVirtualStickEnabled) return

        val leftStick = VirtualStickManager.getInstance().leftStick
        val rightStick = VirtualStickManager.getInstance().rightStick

        leftStick.horizontalPosition = yaw.toInt()
        leftStick.verticalPosition = 0 // Altitude fixe

        rightStick.verticalPosition = pitch.toInt()
        rightStick.horizontalPosition = 0 // Pas de Roll
    }

    /**
     * Récupère l'angle relatif du Gimbal par rapport au nez du drone.
     * Nécessaire pour savoir si le drone doit tourner pour suivre la caméra.
     */
    fun getGimbalYawRelativeToAircraft(): Double {
        return KeyManager.getInstance().getValue(KeyTools.createKey(GimbalKey.KeyYawRelativeToAircraftHeading)) ?: 0.0
    }

    fun stopAll() {
        sendVirtualStick(0.0, 0.0)
        sendGimbalSpeed(0.0, 0.0)
    }

    fun release() {
        stopAll()
        VirtualStickManager.getInstance().disableVirtualStick(null)
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
}
