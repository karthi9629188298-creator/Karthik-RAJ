package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "screening_sessions")
data class ScreeningSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val patientName: String,
    val age: Int,
    val gender: String,
    val educationYears: Int,
    val dateTimestamp: Long = System.currentTimeMillis(),
    val memoryScore: Int,      // e.g. 3-word recall score (0 to 3)
    val orientationScore: Int, // e.g. orientation to time/place (0 to 5)
    val dailyActivityScore: Int, // ADL daily activity score (0 to 10)
    val speechSample: String,   // Recorded speech transcription or language usage text
    val riskScore: Int,         // 0 to 100 percentage
    val riskLevel: String,      // "Low", "Moderate", "High"
    val aiAnalysis: String,     // Clinical summary and explainable AI insights from Gemini
    val demographicRiskWeight: Float,
    val questionnaireRiskWeight: Float,
    val speechRiskWeight: Float,
    val memoryRiskWeight: Float,
    val activityRiskWeight: Float
)

@Dao
interface ScreeningDao {
    @Query("SELECT * FROM screening_sessions ORDER BY dateTimestamp DESC")
    fun getAllSessions(): Flow<List<ScreeningSession>>

    @Query("SELECT * FROM screening_sessions WHERE patientName = :patientName ORDER BY dateTimestamp DESC")
    fun getSessionsForPatient(patientName: String): Flow<List<ScreeningSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ScreeningSession): Long

    @Query("DELETE FROM screening_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Int)

    @Query("DELETE FROM screening_sessions")
    suspend fun clearAllSessions()
}

@Database(entities = [ScreeningSession::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun screeningDao(): ScreeningDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "neurocare_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
