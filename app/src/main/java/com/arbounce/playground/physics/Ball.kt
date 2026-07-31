package com.arbounce.playground.physics

import com.arbounce.playground.math.Vec3

/** A single physics ball in world space (metres). */
class Ball(
    var pos: Vec3,
    var vel: Vec3,
    val radius: Float = 0.055f,
    val color: FloatArray = floatArrayOf(1.0f, 0.44f, 0.26f, 1f),
) {
    val bornAt: Long = System.currentTimeMillis()
    var lastBounceAt: Long = bornAt
    var resting: Boolean = false

    // Impact squash animation: 0 = none, 1 = full squash. Decays over time.
    var squash: Float = 0f
}
