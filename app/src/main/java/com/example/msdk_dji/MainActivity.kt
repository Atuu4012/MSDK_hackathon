package com.example.msdk_dji

import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    // Utilisation de 'by lazy' pour différer l'initialisation jusqu'à ce que 'this' (context) soit prêt
    private val vehicleDetector by lazy { YoloDetector(this, "yolov8n_car.onnx") }
    private val flawDetector by lazy { YoloDetector(this, "best_flaws.onnx") }

    private val droneController = DroneController()
    private val laserManager = LaserRangingManager()

    // La vue personnalisée qui affichera les carrés (doit être dans votre XML ou ajoutée dynamiquement)
    //private lateinit var myOverlayView: OverlayView

    // Mission Manager : initialisé aussi en lazy pour avoir accès aux détecteurs
    private val missionManager by lazy {
        MissionManager(
            droneController,
            laserManager,
            vehicleDetector,
            flawDetector,
            onOverlayUpdate = { boxes, label ->
                // Correction de la signature de la lambda : (boxes, label) -> Unit
                runOnUiThread {
                    // Vérifie si la vue est initialisée
                    //if (::myOverlayView.isInitialized) {
                    //    myOverlayView.updateBoundingBoxes(boxes, label)
                    //}
                }
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Assurez-vous d'avoir un layout

        // Initialisation de la vue (remplacez R.id.overlay_view par votre ID réel)
        // Si vous n'avez pas encore créé la vue dans le XML, créez-la dynamiquement :
        /*
        myOverlayView = findViewById(R.id.overlay_view)
        */
    }

    override fun onDestroy() {
        super.onDestroy()
        vehicleDetector.close()
        flawDetector.close()
        droneController.release()
        laserManager.release()
    }

    // Dans MainActivity

    // Nouveau détecteur pour la piste
    private val padDetector by lazy { YoloDetector(this, "landing_pad.onnx") }

    private val landingManager by lazy {
        LandingMissionManager(
            droneController,
            laserManager,
            padDetector,
            onOverlayUpdate = { boxes, label ->
                // Code complet de mise à jour de l'interface
                runOnUiThread {
                    // Vérifie que la vue est bien initialisée avant d'appeler
                    if (::myOverlayView.isInitialized) {
                        myOverlayView.updateBoundingBoxes(boxes, label)
                    }
                }
            }
        )
    }

    // Bouton pour lancer l'atterrissage
    fun startLandingMission() {
        // On change le callback vidéo pour diriger vers le LandingManager
        currentMission = "LANDING"
    }

    fun onVideoFrameArrived(bitmap: Bitmap) {
        if (currentMission == "INSPECTION") {
            missionManager.processFrame(bitmap)
        } else if (currentMission == "LANDING") {
            landingManager.processFrame(bitmap)
        }
    }

}
