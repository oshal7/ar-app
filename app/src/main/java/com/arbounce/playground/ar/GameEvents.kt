package com.arbounce.playground.ar

/** Callbacks from the GL/physics thread up to the Activity for FX and stats. */
interface GameEvents {
    fun onThrow()
    fun onBounce(impactSpeed: Float, airMs: Long)
    fun onTracking(tracking: Boolean, planeCount: Int)
}
