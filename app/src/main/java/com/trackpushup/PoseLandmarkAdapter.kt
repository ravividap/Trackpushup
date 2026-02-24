package com.trackpushup

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

/**
 * Converts an ML Kit [Pose] into the [Map] of [Landmark] objects expected
 * by [PushupCounter.processLandmarks].
 */
object PoseLandmarkAdapter {

    fun fromMlKitPose(pose: Pose): Map<String, Landmark> {
        val map = mutableMapOf<String, Landmark>()

        fun add(key: String, type: Int) {
            pose.getPoseLandmark(type)?.let { lm ->
                map[key] = Landmark(lm.position.x, lm.position.y, lm.inFrameLikelihood)
            }
        }

        add("LEFT_SHOULDER",  PoseLandmark.LEFT_SHOULDER)
        add("RIGHT_SHOULDER", PoseLandmark.RIGHT_SHOULDER)
        add("LEFT_ELBOW",     PoseLandmark.LEFT_ELBOW)
        add("RIGHT_ELBOW",    PoseLandmark.RIGHT_ELBOW)
        add("LEFT_WRIST",     PoseLandmark.LEFT_WRIST)
        add("RIGHT_WRIST",    PoseLandmark.RIGHT_WRIST)
        add("LEFT_HIP",       PoseLandmark.LEFT_HIP)
        add("RIGHT_HIP",      PoseLandmark.RIGHT_HIP)
        add("LEFT_ANKLE",     PoseLandmark.LEFT_ANKLE)
        add("RIGHT_ANKLE",    PoseLandmark.RIGHT_ANKLE)

        return map
    }
}
