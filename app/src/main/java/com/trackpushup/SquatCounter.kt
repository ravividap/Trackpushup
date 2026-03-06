package com.trackpushup

import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Counts squats by analyzing pose landmarks.
 *
 * A repetition is counted when:
 *  1. The person moves from the STANDING position (knee angle ≥ 160°) to the
 *     DOWN position (knee angle ≤ 90°) and back to STANDING (knee angle ≥ 160°).
 *  2. Key landmarks are detected with sufficient confidence throughout.
 *
 * The knee angle is computed as the hip–knee–ankle angle on each side and then
 * averaged, mirroring the approach used in [PushupCounter].
 */
class SquatCounter : ExerciseCounter {

    enum class SquatState { IDLE, STANDING, DOWN }

    private var state: SquatState = SquatState.IDLE
    override var count: Int = 0
        private set

    /** Feedback message for the user. */
    override var feedback: String = "Get into squat position"
        private set

    /**
     * Process a new set of landmarks and return the updated squat [count].
     *
     * The [landmarks] map must contain entries for the following keys:
     * LEFT_HIP, RIGHT_HIP, LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE.
     */
    override fun processLandmarks(landmarks: Map<String, Landmark>): Int {
        val leftHip    = landmarks["LEFT_HIP"]    ?: return count
        val rightHip   = landmarks["RIGHT_HIP"]   ?: return count
        val leftKnee   = landmarks["LEFT_KNEE"]   ?: return count
        val rightKnee  = landmarks["RIGHT_KNEE"]  ?: return count
        val leftAnkle  = landmarks["LEFT_ANKLE"]  ?: return count
        val rightAnkle = landmarks["RIGHT_ANKLE"] ?: return count

        // Require sufficient confidence for key landmarks
        if (!isConfident(leftHip)    || !isConfident(rightHip)   ||
            !isConfident(leftKnee)   || !isConfident(rightKnee)  ||
            !isConfident(leftAnkle)  || !isConfident(rightAnkle)
        ) {
            feedback = "Make sure your full body is visible"
            return count
        }

        // Average left and right knee angles (hip–knee–ankle)
        val leftKneeAngle  = calculateAngle(leftHip,  leftKnee,  leftAnkle)
        val rightKneeAngle = calculateAngle(rightHip, rightKnee, rightAnkle)
        val avgKneeAngle   = (leftKneeAngle + rightKneeAngle) / 2.0

        when (state) {
            SquatState.IDLE -> {
                if (avgKneeAngle >= KNEE_STANDING_ANGLE) {
                    state = SquatState.STANDING
                    feedback = "Good position! Squat down"
                } else {
                    feedback = "Stand straight to start"
                }
            }

            SquatState.STANDING -> {
                feedback = "Squat down"
                if (avgKneeAngle <= KNEE_DOWN_ANGLE) {
                    state = SquatState.DOWN
                    feedback = "Good squat! Stand back up!"
                }
            }

            SquatState.DOWN -> {
                if (avgKneeAngle >= KNEE_STANDING_ANGLE) {
                    count++
                    feedback = "Great rep! Count: $count"
                    state = SquatState.STANDING
                } else {
                    feedback = "Stand up!"
                }
            }
        }

        return count
    }

    /** Reset counter and state. */
    override fun reset() {
        count = 0
        state = SquatState.IDLE
        feedback = "Get into squat position"
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
        private const val MIN_CONFIDENCE      = 0.5f
        /** Legs considered fully extended (STANDING position). */
        private const val KNEE_STANDING_ANGLE = 160.0
        /** Knees bent sufficiently (DOWN / squat position). */
        private const val KNEE_DOWN_ANGLE     = 90.0
    }
}
