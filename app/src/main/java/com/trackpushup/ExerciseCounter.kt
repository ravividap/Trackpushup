package com.trackpushup

/**
 * Common interface for exercise rep counters so [MainActivity] can work
 * with both [PushupCounter] and [SquatCounter] interchangeably.
 */
interface ExerciseCounter {
    val count: Int
    val feedback: String
    fun processLandmarks(landmarks: Map<String, Landmark>): Int
    fun reset()
}
