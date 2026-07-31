package com.arbounce.playground.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for lifetime stats.
 *
 * DATA-RETENTION CONTRACT
 * -----------------------
 * The version number below only ever goes UP, and every bump ships a [Migration]
 * that ALTERs the existing schema in place. We deliberately do NOT call
 * fallbackToDestructiveMigration(), because that would erase the user's captured
 * data on a schema change. Example, when you later add a column:
 *
 *   val MIGRATION_1_2 = object : Migration(1, 2) {
 *       override fun migrate(db: SupportSQLiteDatabase) {
 *           db.execSQL("ALTER TABLE play_stats ADD COLUMN totalDistanceCm INTEGER NOT NULL DEFAULT 0")
 *       }
 *   }
 *
 * …then set version = 2 and add MIGRATION_1_2 to [ALL_MIGRATIONS].
 */
@Database(entities = [PlayStats::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playStatsDao(): PlayStatsDao

    companion object {
        const val DB_NAME = "ar_bounce.db"

        /** Register future Migration objects here in order. */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf()

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME,
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
