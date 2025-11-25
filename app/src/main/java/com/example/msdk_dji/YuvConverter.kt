package com.example.msdk_dji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import java.io.ByteArrayOutputStream

class YuvConverter(context: Context) {

    // Méthode Rapide (Utilise la classe YuvImage d'Android)
    // C'est suffisant pour du 5-10 FPS
    fun yuvToBitmap(yuvData: ByteArray, width: Int, height: Int): Bitmap? {
        try {
            // DJI envoie souvent du YUV420P (I420) ou NV12.
            // Android YuvImage préfère NV21.
            // Pour simplifier, on essaie de convertir directement.

            val yuvImage = YuvImage(
                yuvData,
                ImageFormat.NV21, // Format standard Android (parfois besoin de swap U/V pour DJI)
                width,
                height,
                null
            )

            val out = ByteArrayOutputStream()
            // On compresse en JPEG pour créer le Bitmap (c'est l'astuce standard)
            // Quality 80 est un bon compromis vitesse/qualité
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 80, out)

            val imageBytes = out.toByteArray()

            // Création du Bitmap final
            // On peut ajouter un downsampling ici (inSampleSize) pour aller plus vite
            val options = BitmapFactory.Options()
            options.inSampleSize = 1 // Mettre à 2 ou 4 si l'image est trop grande (ex: 4K)

            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}