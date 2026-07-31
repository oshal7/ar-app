package com.arbounce.playground.physics

import com.arbounce.playground.math.Vec3
import kotlin.math.abs

/**
 * Fixed-step-ish rigid-body integrator for the balls. Gravity + plane collision
 * with a coefficient of restitution (PRD: 0.75). Pure Kotlin — the caller supplies
 * real-world [Surface]s derived from ARCore planes each frame.
 */
class PhysicsWorld {

    val balls = ArrayList<Ball>()

    var gravity = Vec3(0f, -9.8f, 0f)
    var restitution = 0.75f
    var maxBalls = 8

    private val restSpeed = 0.18f          // below this on a floor → sleep
    private val penetrationSlop = 0.005f

    fun spawn(ball: Ball) {
        balls.add(ball)
        while (balls.size > maxBalls) balls.removeAt(0)
    }

    fun clear() = balls.clear()

    /** Advance the simulation by [dt] seconds. Returns bounce events for FX. */
    fun update(dt: Float, surfaces: List<Surface>): List<BounceEvent> {
        val bounces = ArrayList<BounceEvent>()
        val step = dt.coerceIn(0f, 0.05f) // clamp to keep stability on frame hitches
        val now = System.currentTimeMillis()

        val iterator = balls.iterator()
        while (iterator.hasNext()) {
            val b = iterator.next()

            // squash decays regardless of motion
            if (b.squash > 0f) b.squash = (b.squash - step * 6f).coerceAtLeast(0f)

            if (!b.resting) {
                b.vel = b.vel.addScaled(gravity, step)
                // mild air drag
                b.vel = b.vel * (1f - 0.02f * step)
                b.pos = b.pos.addScaled(b.vel, step)
            }

            // Collisions against every visible surface.
            for (s in surfaces) {
                val toBall = b.pos - s.point
                val d = toBall.dot(s.normal)            // signed distance to plane
                val approaching = b.vel.dot(s.normal) < 0f
                val penetrating = d <= b.radius && d > -(b.radius + 0.15f)
                if (!penetrating || !approaching) continue

                val projected = b.pos.addScaled(s.normal, -d) // point on plane
                if (!s.inPolygon(projected)) continue

                val vn = b.vel.dot(s.normal)
                val impact = abs(vn)
                // Reflect the normal component (restitution); apply friction to the
                // tangential component so the ball loses a little sideways speed.
                val reflected = b.vel.addScaled(s.normal, -(1f + restitution) * vn)
                val vnAfter = reflected.dot(s.normal)
                val tangential = reflected.addScaled(s.normal, -vnAfter)
                b.vel = (tangential * 0.9f).addScaled(s.normal, vnAfter)
                // push out of the surface
                b.pos = projected.addScaled(s.normal, b.radius + penetrationSlop)

                b.squash = (impact / 4f).coerceIn(0.25f, 1f)
                val airMs = now - b.lastBounceAt
                b.lastBounceAt = now
                bounces.add(BounceEvent(b.pos, impact, airMs))

                // Sleep on near-horizontal floors once slow enough.
                if (s.isFloorLike && b.vel.length() < restSpeed) {
                    b.vel = Vec3(0f, 0f, 0f)
                    b.resting = true
                }
            }

            // Cleanup: gone far, fell through the world, or long-settled.
            val age = now - b.bornAt
            val far = b.pos.length() > 20f || b.pos.y < -6f
            val staleRest = b.resting && age > 25_000
            if (far || staleRest) iterator.remove()
        }
        return bounces
    }

    /**
     * Predict a trajectory (world points) for the aiming preview, without touching
     * live balls. Simple gravity-only forward Euler.
     */
    fun predict(start: Vec3, velocity: Vec3, seconds: Float, samples: Int): FloatArray {
        val out = FloatArray(samples * 3)
        var p = start
        var v = velocity
        val step = seconds / samples
        for (i in 0 until samples) {
            v = v.addScaled(gravity, step)
            p = p.addScaled(v, step)
            out[i * 3] = p.x; out[i * 3 + 1] = p.y; out[i * 3 + 2] = p.z
        }
        return out
    }
}
