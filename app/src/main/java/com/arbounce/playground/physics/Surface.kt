package com.arbounce.playground.physics

import com.arbounce.playground.math.Vec3

/**
 * A collidable real-world surface, decoupled from ARCore so the physics layer has
 * no SDK dependency. [normal] is the unit surface normal, [point] is any point on
 * the plane, and [inPolygon] reports whether a world point projects inside the
 * detected plane's extent.
 */
class Surface(
    val normal: Vec3,
    val point: Vec3,
    val isFloorLike: Boolean,
    val inPolygon: (Vec3) -> Boolean,
)

/** Emitted when a ball strikes a surface, so callers can do FX / stats. */
data class BounceEvent(val pos: Vec3, val impactSpeed: Float, val airMs: Long)
