package com.trackpushup

import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Lightweight landmark data class so [PushupCounter] does not depend
 * directly on ML Kit or Android framework types and can be fully unit-tested
 * on the JVM.
 *
 * @param x  Horizontal pixel coordinate.
 * @param y  Vertical pixel coordinate.
 * @param confidence How likely the landmark is visible in the frame (0–1).
 */
data class Landmark(val x: Float, val y: Float, val confidence: Float)

/**
 * Counts pushups by analyzing pose landmarks.
 *
 * A repetition is counted when:
 *  1. The person moves from the UP position (arms extended) to the DOWN position
 *     (elbows bent ≤ 90°) and back to the UP position (elbows ≥ 160°).
 *  2. Body alignment is correct throughout the DOWN phase (shoulder–hip–ankle
 *     angle ≥ MIN_BODY_ANGLE degrees, i.e. body is roughly straight).
 *
 * Use [PoseLandmarkAdapter.fromMlKitPose] to convert an ML Kit
 * [com.google.mlkit.vision.pose.Pose] into the map expected by [processLandmarks].
 */
class PushupCounter {

    enum class PushupState { IDLE, UP, DOWN }

    private var state: PushupState = PushupState.IDLE
    private var formGoodDuringDown = false
    var count: Int = 0
        private set

    /** Feedback message for the user. */
    var feedback: String = "Get into push-up position"
        private set

    /**
     * Process a new set of landmarks and return the updated pushup [count].
     *
     * The [landmarks] map must contain entries for the following keys:
     * LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_ELBOW, RIGHT_ELBOW, LEFT_WRIST,
     * RIGHT_WRIST, LEFT_HIP, RIGHT_HIP, LEFT_ANKLE, RIGHT_ANKLE.
     */
    fun processLandmarks(landmarks: Map<String, Landmark>): Int {
        val leftShoulder  = landmarks["LEFT_SHOULDER"]  ?: return count
        val rightShoulder = landmarks["RIGHT_SHOULDER"] ?: return count
        val leftElbow     = landmarks["LEFT_ELBOW"]     ?: return count
        val rightElbow    = landmarks["RIGHT_ELBOW"]    ?: return count
        val leftWrist     = landmarks["LEFT_WRIST"]     ?: return count
        val rightWrist    = landmarks["RIGHT_WRIST"]    ?: return count
        val leftHip       = landmarks["LEFT_HIP"]       ?: return count
        val rightHip      = landmarks["RIGHT_HIP"]      ?: return count
        val leftAnkle     = landmarks["LEFT_ANKLE"]     ?: return count
        val rightAnkle    = landmarks["RIGHT_ANKLE"]    ?: return count

        // Require sufficient confidence for key landmarks
        if (!isConfident(leftShoulder) || !isConfident(rightShoulder) ||
            !isConfident(leftElbow)    || !isConfident(rightElbow)    ||
            !isConfident(leftWrist)    || !isConfident(rightWrist)    ||
            !isConfident(leftHip)      || !isConfident(rightHip)
        ) {
            feedback = "Make sure your full body is visible"
            return count
        }

        // Average left and right elbow angles
        val leftElbowAngle  = calculateAngle(leftShoulder, leftElbow, leftWrist)
        val rightElbowAngle = calculateAngle(rightShoulder, rightElbow, rightWrist)
        val avgElbowAngle   = (leftElbowAngle + rightElbowAngle) / 2.0

        // Body alignment: shoulder–hip–ankle angle (straight plank = ~180°)
        val leftBodyAngle  = calculateAngle(leftShoulder, leftHip, leftAnkle)
        val rightBodyAngle = calculateAngle(rightShoulder, rightHip, rightAnkle)
        val avgBodyAngle   = (leftBodyAngle + rightBodyAngle) / 2.0

        val bodyAligned = avgBodyAngle >= MIN_BODY_ANGLE

        when (state) {
            PushupState.IDLE -> {
                if (avgElbowAngle >= ELBOW_UP_ANGLE) {
                    state = PushupState.UP
                    feedback = if (bodyAligned) "Good plank position! Go down" else "Straighten your body"
                } else {
                    feedback = "Extend your arms to start"
                }
            }

            PushupState.UP -> {
                feedback = if (bodyAligned) "Good position! Lower yourself" else "Keep your body straight"
                if (avgElbowAngle <= ELBOW_DOWN_ANGLE) {
                    state = PushupState.DOWN
                    formGoodDuringDown = bodyAligned
                    feedback = if (bodyAligned) "Good form! Push up!" else "Keep hips down!"
                }
            }

            PushupState.DOWN -> {
                if (!bodyAligned) {
                    formGoodDuringDown = false
                }
                if (avgElbowAngle >= ELBOW_UP_ANGLE) {
                    if (formGoodDuringDown) {
                        count++
                        feedback = "Great rep! Count: $count"
                    } else {
                        feedback = "Bad form – keep your body straight"
                    }
                    state = PushupState.UP
                } else {
                    feedback = if (bodyAligned) "Push up!" else "Straighten your body first!"
                }
            }
        }

        return count
    }

    /** Reset counter and state. */
    fun reset() {
        count = 0
        state = PushupState.IDLE
        formGoodDuringDown = false
        feedback = "Get into push-up position"
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun isConfident(landmark: Landmark): Boolean =
        landmark.confidence >= MIN_CONFIDENCE

    /**
     * Returns the angle (degrees) at [middle] formed by [first]–[middle]–[last].
     */
    private fun calculateAngle(
        first: Landmark,
        middle: Landmark,
        last: Landmark
    ): Double {
        val ax = first.x.toDouble()
        val ay = first.y.toDouble()
        val bx = middle.x.toDouble()
        val by = middle.y.toDouble()
        val cx = last.x.toDouble()
        val cy = last.y.toDouble()

        val abx = ax - bx; val aby = ay - by
        val cbx = cx - bx; val cby = cy - by

        val dot   = abx * cbx + aby * cby
        val magAB = sqrt(abx * abx + aby * aby)
        val magCB = sqrt(cbx * cbx + cby * cby)

        if (magAB == 0.0 || magCB == 0.0) return 0.0

        val cosAngle = (dot / (magAB * magCB)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosAngle))
    }

    companion object {
        private const val MIN_CONFIDENCE   = 0.5f
        /** Arms considered fully extended (UP position). */
        private const val ELBOW_UP_ANGLE   = 160.0
        /** Arms considered bent sufficiently (DOWN position). */
        private const val ELBOW_DOWN_ANGLE = 90.0
        /** Minimum shoulder–hip–ankle angle to consider body aligned. */
        private const val MIN_BODY_ANGLE   = 150.0
    }
}
