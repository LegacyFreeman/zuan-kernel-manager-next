package com.zuan.kernelmanager.next.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "fps_sessions")
data class FpsSession(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val sampleIntervalMs: Long,
    val sampleCount: Int,
    val validFpsSamples: Int,
    val avgFps: Float,
    val minFps: Float,
    val maxFps: Float,
    val onePercentLow: Float,
    val zeroOnePercentLow: Float,
    val frameTimeP95Ms: Float,
    val jankPercent: Float,
    val stutterCount: Int,
    val variance: Float,
    val smoothness: Float,
    val avgCpu: Float,
    val maxCpu: Int,
    val avgGpuLoad: Float,
    val maxGpuLoad: Int,
    val avgTemp: Float,
    val maxTemp: Float,
    val avgWatt: Float,
    val maxWatt: Float,
    val avgRam: Int,
    val maxRam: Int,
    val avgRamPercent: Float,
    val maxRamPercent: Int,
    val avgCpuFreqMHz: Float,
    val maxCpuFreqMHz: Float,
    val avgGpuFreqMHz: Float,
    val maxGpuFreqMHz: Float,
    val startBattery: Int,
    val endBattery: Int,
    val avgBattery: Float,
    val minBattery: Int,
    val avgVoltageMv: Float,
    val avgCurrentMa: Float,
    val refreshRateHz: Float,
    val renderer: String,
    val fpsSource: String
)

@Entity(
    tableName = "fps_points",
    foreignKeys = [ForeignKey(
        entity = FpsSession::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["sessionId"])]
)
data class FpsDataPoint(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val fps: Float,
    val frameTimeMs: Float,
    val cpuLoad: Int,
    val cpuFreqMHz: Float,
    val gpuLoad: Int,
    val gpuFreqMHz: Float,
    val temp: Float,
    val watt: Float,
    val ramUsageMb: Int,
    val ramTotalMb: Int,
    val ramPercent: Int,
    val batteryLevel: Int,
    val batteryVoltageMv: Float,
    val batteryCurrentMa: Float,
    val refreshRateHz: Float,
    val jank: Boolean,
    val renderer: String,
    val foregroundPackage: String
)

@Dao
interface FpsDao {
    @Insert
    suspend fun insertSession(session: FpsSession): Long

    @Insert
    suspend fun insertDataPoints(points: List<FpsDataPoint>)

    @Query("SELECT * FROM fps_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<FpsSession>>

    @Query("SELECT * FROM fps_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getPointsForSession(sessionId: Long): List<FpsDataPoint>

    @Query("DELETE FROM fps_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM fps_sessions")
    suspend fun clearAll()
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        fun add(table: String, column: String, type: String, default: String) {
            database.execSQL("ALTER TABLE $table ADD COLUMN $column $type NOT NULL DEFAULT $default")
        }

        add("fps_sessions", "sampleIntervalMs", "INTEGER", "1000")
        add("fps_sessions", "sampleCount", "INTEGER", "0")
        add("fps_sessions", "validFpsSamples", "INTEGER", "0")
        add("fps_sessions", "onePercentLow", "REAL", "0")
        add("fps_sessions", "zeroOnePercentLow", "REAL", "0")
        add("fps_sessions", "frameTimeP95Ms", "REAL", "0")
        add("fps_sessions", "jankPercent", "REAL", "0")
        add("fps_sessions", "stutterCount", "INTEGER", "0")
        add("fps_sessions", "avgCpu", "REAL", "0")
        add("fps_sessions", "maxCpu", "INTEGER", "0")
        add("fps_sessions", "avgGpuLoad", "REAL", "0")
        add("fps_sessions", "maxGpuLoad", "INTEGER", "0")
        add("fps_sessions", "avgRamPercent", "REAL", "0")
        add("fps_sessions", "maxRamPercent", "INTEGER", "0")
        add("fps_sessions", "avgCpuFreqMHz", "REAL", "0")
        add("fps_sessions", "maxCpuFreqMHz", "REAL", "0")
        add("fps_sessions", "avgGpuFreqMHz", "REAL", "0")
        add("fps_sessions", "maxGpuFreqMHz", "REAL", "0")
        add("fps_sessions", "startBattery", "INTEGER", "0")
        add("fps_sessions", "endBattery", "INTEGER", "0")
        add("fps_sessions", "avgBattery", "REAL", "0")
        add("fps_sessions", "minBattery", "INTEGER", "0")
        add("fps_sessions", "avgVoltageMv", "REAL", "0")
        add("fps_sessions", "avgCurrentMa", "REAL", "0")
        add("fps_sessions", "refreshRateHz", "REAL", "0")
        add("fps_sessions", "renderer", "TEXT", "''")
        add("fps_sessions", "fpsSource", "TEXT", "''")

        add("fps_points", "frameTimeMs", "REAL", "0")
        add("fps_points", "cpuFreqMHz", "REAL", "0")
        add("fps_points", "gpuLoad", "INTEGER", "0")
        add("fps_points", "gpuFreqMHz", "REAL", "0")
        add("fps_points", "ramTotalMb", "INTEGER", "0")
        add("fps_points", "ramPercent", "INTEGER", "0")
        add("fps_points", "batteryLevel", "INTEGER", "0")
        add("fps_points", "batteryVoltageMv", "REAL", "0")
        add("fps_points", "batteryCurrentMa", "REAL", "0")
        add("fps_points", "refreshRateHz", "REAL", "0")
        add("fps_points", "jank", "INTEGER", "0")
        add("fps_points", "renderer", "TEXT", "''")
        add("fps_points", "foregroundPackage", "TEXT", "''")
    }
}

@Database(entities = [FpsSession::class, FpsDataPoint::class], version = 3, exportSchema = false)
abstract class FpsDatabase : RoomDatabase() {
    abstract fun fpsDao(): FpsDao

    companion object {
        @Volatile private var INSTANCE: FpsDatabase? = null

        fun getDatabase(context: Context): FpsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FpsDatabase::class.java,
                    "zkm_fps_db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
