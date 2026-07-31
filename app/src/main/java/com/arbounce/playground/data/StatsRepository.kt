package com.arbounce.playground.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Thin async facade over [PlayStatsDao]. Buffers increments in memory and flushes
 * them to Room so the hot render/physics loop never touches disk. Exposes a
 * [StateFlow] the HUD observes for live lifetime totals.
 */
class StatsRepository(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val dao = AppDatabase.get(context).playStatsDao()

    private val _stats = MutableStateFlow(PlayStats())
    val stats: Flow<PlayStats> = _stats.asStateFlow()

    // Pending, un-flushed deltas.
    private var pendingThrows = 0L
    private var pendingBounces = 0L
    private var pendingPlayMs = 0L
    private var pendingBestAirMs = 0L
    private var lastEntity = "ball"

    init {
        scope.launch(Dispatchers.IO) {
            dao.insertIfAbsent(PlayStats())
            dao.get()?.let { _stats.value = it }
        }
    }

    fun onThrow(entity: String) {
        pendingThrows++
        lastEntity = entity
        bumpLocal(throws = 1, entity = entity)
    }

    fun onBounce() {
        pendingBounces++
        bumpLocal(bounces = 1)
    }

    /** Report an airborne duration; keeps the max. */
    fun onAirTime(ms: Long) {
        if (ms > pendingBestAirMs) pendingBestAirMs = ms
        val cur = _stats.value
        if (ms > cur.bestAirTimeMs) _stats.value = cur.copy(bestAirTimeMs = ms)
    }

    fun addPlayTime(ms: Long) {
        pendingPlayMs += ms
    }

    /** Optimistically update the observed value so the HUD reacts instantly. */
    private fun bumpLocal(throws: Long = 0, bounces: Long = 0, entity: String? = null) {
        val cur = _stats.value
        _stats.value = cur.copy(
            totalThrows = cur.totalThrows + throws,
            totalBounces = cur.totalBounces + bounces,
            lastEntity = entity ?: cur.lastEntity,
        )
    }

    /** Persist buffered deltas. Safe to call periodically and on pause. */
    suspend fun flush() = withContext(Dispatchers.IO) {
        val t = pendingThrows; val b = pendingBounces
        val p = pendingPlayMs; val air = pendingBestAirMs
        if (t == 0L && b == 0L && p == 0L && air == 0L) return@withContext
        pendingThrows = 0; pendingBounces = 0; pendingPlayMs = 0; pendingBestAirMs = 0
        dao.accumulate(
            throws = t,
            bounces = b,
            playTimeMs = p,
            airTimeMs = air,
            lastEntity = lastEntity,
        )
        dao.get()?.let { _stats.value = it }
    }
}
