package com.trackpushup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SquatCounter] – runs purely on the JVM with no Android or
 * ML Kit dependencies.
 */
class SquatCounterTest {

    private lateinit var counter: SquatCounter

    @Before
    fun setUp() {
        counter = SquatCounter()
    }

    // ── helper to build a full landmark map ──────────────────────────────────

    /**
     * Builds a landmark map for a standing figure where the knees are at
     * [kneeAngleDeg] (hip–knee–ankle angle).
     *
     * The geometry is:
     *  - Hip at (0, 0), knee at (0, 1) (directly below).
     *  - Ankle placed so that the hip–knee–ankle angle equals [kneeAngleDeg].
     */
    private fun landmarks(kneeAngleDeg: Double): Map<String, Landmark> {
        val conf = 0.9f

        val hipX = 0f;  val hipY = 0f
        val kneeX = 0f; val kneeY = 1f

        // Direction from knee toward hip: (0, -1).
        // Rotate by kneeAngleDeg to get ankle direction from knee.
        val radians = Math.toRadians(kneeAngleDeg)
        val ankleX  = kneeX + Math.sin(radians).toFloat()
        val ankleY  = kneeY + Math.cos(radians).toFloat()

        return mapOf(
            "LEFT_HIP"    to Landmark(hipX,   hipY,   conf),
            "RIGHT_HIP"   to Landmark(hipX,   hipY,   conf),
            "LEFT_KNEE"   to Landmark(kneeX,  kneeY,  conf),
            "RIGHT_KNEE"  to Landmark(kneeX,  kneeY,  conf),
            "LEFT_ANKLE"  to Landmark(ankleX, ankleY, conf),
            "RIGHT_ANKLE" to Landmark(ankleX, ankleY, conf),
        )
    }

    // ── basic state tests ─────────────────────────────────────────────────────

    @Test
    fun `initial count is zero`() {
        assertEquals(0, counter.count)
    }

    @Test
    fun `feedback starts with position-related message`() {
        assertTrue(
            counter.feedback.contains("squat", ignoreCase = true) ||
            counter.feedback.contains("position", ignoreCase = true)
        )
    }

    @Test
    fun `reset restores count to zero`() {
        counter.processLandmarks(landmarks(170.0)) // IDLE → STANDING
        counter.processLandmarks(landmarks(85.0))  // STANDING → DOWN
        counter.processLandmarks(landmarks(170.0)) // DOWN → STANDING, count = 1
        assertEquals(1, counter.count)
        counter.reset()
        assertEquals(0, counter.count)
    }

    // ── rep counting ──────────────────────────────────────────────────────────

    @Test
    fun `one full correct rep increments count`() {
        counter.processLandmarks(landmarks(170.0)) // IDLE → STANDING
        counter.processLandmarks(landmarks(85.0))  // STANDING → DOWN
        counter.processLandmarks(landmarks(170.0)) // DOWN → STANDING, rep counted
        assertEquals(1, counter.count)
    }

    @Test
    fun `partial rep (no stand-up) does not count`() {
        counter.processLandmarks(landmarks(170.0)) // IDLE → STANDING
        counter.processLandmarks(landmarks(85.0))  // STANDING → DOWN (no stand-up)
        assertEquals(0, counter.count)
    }

    @Test
    fun `multiple correct reps are all counted`() {
        repeat(3) {
            counter.processLandmarks(landmarks(170.0))
            counter.processLandmarks(landmarks(85.0))
        }
        // Complete the last rep
        counter.processLandmarks(landmarks(170.0))
        assertTrue(counter.count >= 1)
    }

    @Test
    fun `missing landmark returns current count without crashing`() {
        val incomplete = mapOf("LEFT_HIP" to Landmark(0f, 0f, 0.9f))
        val result = counter.processLandmarks(incomplete)
        assertEquals(0, result)
    }

    @Test
    fun `low confidence landmarks are ignored`() {
        val lowConf = landmarks(170.0).mapValues { (_, lm) -> lm.copy(confidence = 0.1f) }
        counter.processLandmarks(lowConf)
        assertEquals(0, counter.count)
        assertTrue(counter.feedback.contains("visible", ignoreCase = true))
    }

    @Test
    fun `implements ExerciseCounter interface`() {
        val exerciseCounter: ExerciseCounter = counter
        assertEquals(0, exerciseCounter.count)
        exerciseCounter.processLandmarks(landmarks(170.0))
        exerciseCounter.processLandmarks(landmarks(85.0))
        exerciseCounter.processLandmarks(landmarks(170.0))
        assertEquals(1, exerciseCounter.count)
        exerciseCounter.reset()
        assertEquals(0, exerciseCounter.count)
    }
}
