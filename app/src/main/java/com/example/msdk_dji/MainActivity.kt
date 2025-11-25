package com.example.msdk_dji

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// --- IMPORTS VIDÉO MSDK 5.17.0 ---
import dji.v5.common.video.stream.StreamSource
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.interfaces.IVideoDecoder
import dji.v5.manager.interfaces.IVideoDecoder.VideoFrameListener
// ---------------------------------

class MainActivity : AppCompatActivity() {

    private lateinit var yoloDetector: YoloDetector
    private lateinit var droneController: DroneController
    private lateinit var yuvConverter: YuvConverter

    private var videoDecoder: IVideoDecoder? = null
    private var isProcessingFrame = false

    // On garde une référence au listener pour pouvoir l'enlever proprement
    private val frameListener = object : VideoFrameListener {
        override fun onFrame(
            yuvData: ByteArray,
            width: Int,
            height: Int,
            format: IVideoDecoder.VideoFrameFormat
        ) {
            processVideoFrame(yuvData, width, height)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initModules()
        initDJIVideoListener()
    }

    private fun initModules() {
        try {
            yuvConverter = YuvConverter(this)
            droneController = DroneController()
            // Assurez-vous que best.onnx est bien dans app/src/main/assets/
            yoloDetector = YoloDetector(this, "best.onnx")

            Toast.makeText(this, "Système IA Prêt", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("DJI_AI", "Erreur Init: ${e.message}")
        }
    }

    private fun initDJIVideoListener() {
        // Récupération du gestionnaire de flux
        val streamManager = MediaDataCenter.getInstance().videoStreamManager

        // On demande le décodeur pour la caméra principale (M30T = Zoom ou Wide selon config)
        // Note: CAMERA_SOURCE_PRIMARY doit être importé de StreamSource
        val source = StreamSource.CAMERA_SOURCE_PRIMARY

        videoDecoder = streamManager.getAvailableVideoDecoder(source)

        if (videoDecoder != null) {
            // On ajoute le listener défini plus haut
            videoDecoder?.addVideoFrameListener(frameListener)
            Log.i("DJI_AI", "Ecoute vidéo démarrée")
        } else {
            Log.e("DJI_AI", "Décodeur vidéo indisponible (Drone connecté ?)")
        }
    }

    private fun processVideoFrame(yuvData: ByteArray, width: Int, height: Int) {
        if (isProcessingFrame) return
        isProcessingFrame = true

        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Conversion YUV -> Bitmap
                val bitmap = yuvConverter.yuvToBitmap(yuvData, width, height)

                if (bitmap != null) {
                    // Inférence YOLO
                    val result = yoloDetector.detect(bitmap)

                    if (result != null) {
                        Log.d("DJI_AI", "Détection : ${result.score}")
                        // Pilotage
                        droneController.trackAndMeasure(result.box, width, height)
                    }
                }
            } catch (e: Exception) {
                Log.e("DJI_AI", "Erreur processing: ${e.message}")
            } finally {
                isProcessingFrame = false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Nettoyage propre du listener
        videoDecoder?.removeVideoFrameListener(frameListener)
    }
}