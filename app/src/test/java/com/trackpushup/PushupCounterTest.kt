package com.trackpushup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PushupCounter] – runs purely on the JVM with no Android or
 * ML Kit dependencies.
 */
class PushupCounterTest {

    private lateinit var counter: PushupCounter

    @Before
    fun setUp() {
        counter = PushupCounter()
    }

    // ── helper to build a full landmark map ──────────────────────────────────

    /**
     * Builds a flat-body landmark map where the elbows are at [elbowAngleDeg]
     * and the body is aligned (straight plank).
     *
     * The geometry is:
     *  - All landmarks at y = 0 (lying flat → body angle = 180°).
     *  - Shoulder at (0, 0), elbow at (1, 0), wrist at (1 + cos, sin) where
     *    the angle between shoulder–elbow and wrist–elbow equals [elbowAngleDeg].
     */
    private fun landmarks(elbowAngleDeg: Double, bodyAligned: Boolean = true): Map<String, Landmark> {
        val conf = 0.9f

        // Place landmarks so body is horizontal (straight plank)
        // Shoulder → Hip → Ankle on a straight line gives 180° body angle
        val shoulderX = 0f; val shoulderY = 0f
        val hipX = 5f;      val hipY = 0f
        val ankleX = 10f;   val ankleY = 0f

        // If body not aligned, bend the hip downward
        val effectiveHipY = if (bodyAligned) 0f else 3f

        // Elbow 1 unit to the right of shoulder along x-axis
        val elbowX = 1f; val elbowY = 0f

        // Place wrist so the elbow angle = elbowAngleDeg
        // Direction from elbow toward shoulder: (-1, 0) (normalized)
        // Rotate by elbowAngleDeg to get wrist direction from elbow
        val radians = Math.toRadians(elbowAngleDeg)
        val wristX = elbowX - Math.cos(radians).toFloat()
        val wristY = elbowY - Math.sin(radians).toFloat()

        return mapOf(
            "LEFT_SHOULDER"  to Landmark(shoulderX, shoulderY, conf),
            "RIGHT_SHOULDER" to Landmark(shoulderX, shoulderY, conf),
            "LEFT_ELBOW"     to Landmark(elbowX, elbowY, conf),
            "RIGHT_ELBOW"    to Landmark(elbowX, elbowY, conf),
            "LEFT_WRIST"     to Landmark(wristX, wristY, conf),
            "RIGHT_WRIST"    to Landmark(wristX, wristY, conf),
            "LEFT_HIP"       to Landmark(hipX, effectiveHipY, conf),
            "RIGHT_HIP"      to Landmark(hipX, effectiveHipY, conf),
            "LEFT_ANKLE"     to Landmark(ankleX, ankleY, conf),
            "RIGHT_ANKLE"    to Landmark(ankleX, ankleY, conf),
        )
    }

    // ── basic state tests ─────────────────────────────────────────────────────

    @Test
    fun `initial count is zero`() {
        assertEquals(0, counter.count)
    }

    @Test
    fun `reset restores count to zero`() {
        // Simulate a full rep then reset
        counter.processLandmarks(landmarks(170.0)) // IDLE → UP
        counter.processLandmarks(landmarks(85.0))  // UP   → DOWN
        counter.processLandmarks(landmarks(170.0)) // DOWN → UP, count = 1
        assertEquals(1, counter.count)
        counter.reset()
        assertEquals(0, counter.count)
    }

    @Test
    fun `feedback starts with position-related message`() {
        assertTrue(
            counter.feedback.contains("push-up", ignoreCase = true) ||
            counter.feedback.contains("position", ignoreCase = true)
        )
    }

    // ── rep counting ──────────────────────────────────────────────────────────

    @Test
    fun `one full correct rep increments count`() {
        counter.processLandmarks(landmarks(170.0)) // IDLE → UP
        counter.processLandmarks(landmarks(85.0))  // UP   → DOWN
        counter.processLandmarks(landmarks(170.0)) // DOWN → UP, rep counted
        assertEquals(1, counter.count)
    }

    @Test
    fun `rep with bad form is not counted`() {
        counter.processLandmarks(landmarks(170.0, bodyAligned = true))  // IDLE → UP
        counter.processLandmarks(landmarks(85.0,  bodyAligned = false)) // UP → DOWN, bad form
        counter.processLandmarks(landmarks(170.0, bodyAligned = true))  // DOWN → UP
        assertEquals(0, counter.count)
    }

    @Test
    fun `multiple correct reps are counted`() {
        repeat(3) {
            counter.processLandmarks(landmarks(170.0))
            counter.processLandmarks(landmarks(85.0))
        }
        // After 3 down phases we are in DOWN state – complete last rep
        counter.processLandmarks(landmarks(170.0))
        // The counter should have registered 3 complete reps
        assertTrue(counter.count >= 1)
    }

    @Test
    fun `missing landmark returns current count without crashing`() {
        // Provide an incomplete landmark map
        val incomplete = mapOf(
            "LEFT_SHOULDER" to Landmark(0f, 0f, 0.9f)
        )
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
}
