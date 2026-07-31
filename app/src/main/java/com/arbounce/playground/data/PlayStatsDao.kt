package com.arbounce.playground.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayStatsDao {

    @Query("SELECT * FROM play_stats WHERE id = :id LIMIT 1")
    suspend fun get(id: Int = PlayStats.SINGLETON_ID): PlayStats?

    @Query("SELECT * FROM play_stats WHERE id = :id LIMIT 1")
    fun observe(id: Int = PlayStats.SINGLETON_ID): Flow<PlayStats?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(stats: PlayStats): Long

    @Query(
        """
        UPDATE play_stats
        SET totalThrows = totalThrows + :throwsDelta,
            totalBounces = totalBounces + :bouncesDelta,
            totalPlayTimeMs = totalPlayTimeMs + :playTimeMs,
            bestAirTimeMs = CASE WHEN :airTimeMs > bestAirTimeMs THEN :airTimeMs ELSE bestAirTimeMs END,
            lastEntity = :lastEntity,
            updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun accumulate(
        throwsDelta: Long,
        bouncesDelta: Long,
        playTimeMs: Long,
        airTimeMs: Long,
        lastEntity: String,
        now: Long = System.currentTimeMillis(),
        id: Int = PlayStats.SINGLETON_ID,
    )
}
