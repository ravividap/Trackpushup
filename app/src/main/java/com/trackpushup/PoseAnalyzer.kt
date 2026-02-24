package com.trackpushup

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

/**
 * CameraX [ImageAnalysis.Analyzer] that runs ML Kit Pose Detection on every
 * camera frame and forwards detected poses (as [Map] of [Landmark]) to [onPoseDetected].
 */
class PoseAnalyzer(
    private val onPoseDetected: (Map<String, Landmark>) -> Unit
) : ImageAnalysis.Analyzer {

    private val options = PoseDetectorOptions.Builder()
        .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
        .build()

    private val detector: PoseDetector = PoseDetection.getClient(options)

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(image)
            .addOnSuccessListener { pose ->
                if (pose.allPoseLandmarks.isNotEmpty()) {
                    onPoseDetected(PoseLandmarkAdapter.fromMlKitPose(pose))
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
