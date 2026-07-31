package com.arbounce.playground.math

import kotlin.math.sqrt

/** Minimal mutable-friendly 3D vector used by the physics layer. */
data class Vec3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {

    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)

    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalized(): Vec3 {
        val l = length()
        return if (l < 1e-6f) Vec3(0f, 0f, 0f) else Vec3(x / l, y / l, z / l)
    }

    fun addScaled(o: Vec3, s: Float): Vec3 = Vec3(x + o.x * s, y + o.y * s, z + o.z * s)

    fun toFloatArray(): FloatArray = floatArrayOf(x, y, z)

    companion object {
        fun of(a: FloatArray, off: Int = 0) = Vec3(a[off], a[off + 1], a[off + 2])
    }
}
