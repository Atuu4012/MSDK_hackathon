package com.example.msdk_dji

import android.graphics.RectF

data class DetectionResult(
    val detection: Int,
    val classIndex: Int,
    val score: Float,
    val boundingBox: RectF // En pixels (ex: 0..1920, 0..1080)
)
