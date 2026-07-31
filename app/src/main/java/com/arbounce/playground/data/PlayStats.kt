package com.arbounce.playground.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cumulative lifetime play statistics. This is a single-row table (id is always
 * [SINGLETON_ID]) that accumulates across every session and — critically —
 * across app updates. Because it lives in Room / internal storage and the app is
 * always signed with the same key, an in-place update keeps this data intact.
 *
 * When you add fields in a future version, bump the DB version in [AppDatabase]
 * and supply a Room Migration that ALTERs this table. Never wipe it.
 */
@Entity(tableName = "play_stats")
data class PlayStats(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val totalThrows: Long = 0,
    val totalBounces: Long = 0,
    val bestAirTimeMs: Long = 0,
    val totalPlayTimeMs: Long = 0,
    val lastEntity: String = "ball",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
