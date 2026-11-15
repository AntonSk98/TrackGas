package com.trackgas.app.temperature

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.withTransaction
import com.trackgas.app.data.ReadingDatabase
import com.trackgas.app.data.toReading
import kotlinx.coroutines.flow.Flow

/** Only the completed mean and the exact inputs used to obtain it are durable. */
@Entity(tableName = "interval_temperatures", primaryKeys = ["earlierId", "laterId"])
data class IntervalTemperatureEntity(
    val earlierId: String,
    val laterId: String,
    val installationId: String,
    val startEpoch: Long,
    val endEpoch: Long,
    val location: String,
    val celsius: String,
)

@Entity(tableName = "daily_temperatures", primaryKeys = ["date", "location"])
data class DailyTemperatureEntity(
    val date: String,
    val location: String,
    val celsius: String,
)

@Dao
interface TemperatureDao {
    @Query("SELECT * FROM interval_temperatures")
    fun observeAll(): Flow<List<IntervalTemperatureEntity>>

    @Query("SELECT * FROM interval_temperatures")
    suspend fun getAll(): List<IntervalTemperatureEntity>

    @Query("SELECT * FROM daily_temperatures")
    fun observeAllDaily(): Flow<List<DailyTemperatureEntity>>

    @Query("SELECT * FROM daily_temperatures")
    suspend fun getAllDaily(): List<DailyTemperatureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(temperature: IntervalTemperatureEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDaily(temperature: DailyTemperatureEntity)

    @Query("""DELETE FROM interval_temperatures WHERE NOT EXISTS (
        SELECT 1 FROM readings a JOIN readings b ON a.installationId = b.installationId
        WHERE a.id = earlierId AND b.id = laterId
        AND a.installationId = interval_temperatures.installationId
        AND a.observedAt = startEpoch AND b.observedAt = endEpoch AND a.date < b.date
        AND NOT EXISTS (SELECT 1 FROM readings c WHERE c.installationId = a.installationId
            AND c.date > a.date AND c.date < b.date))""")
    suspend fun pruneObsoleteIntervals()

    @Query("""DELETE FROM daily_temperatures WHERE NOT EXISTS (
        SELECT 1 FROM readings a JOIN readings b ON a.installationId = b.installationId
        WHERE a.date < b.date
        AND daily_temperatures.date > a.date AND daily_temperatures.date <= b.date
        AND NOT EXISTS (SELECT 1 FROM readings c WHERE c.installationId = a.installationId
            AND c.date > a.date AND c.date < b.date))""")
    suspend fun pruneObsoleteDaily()

    @Query("DELETE FROM interval_temperatures")
    suspend fun deleteAll()

    @Query("DELETE FROM daily_temperatures")
    suspend fun deleteAllDaily()
}

interface TemperatureRepository {
    fun observe(): Flow<List<IntervalTemperatureEntity>>
    suspend fun stored(): List<IntervalTemperatureEntity>
    suspend fun storeIfCurrent(temperature: IntervalTemperatureEntity): Boolean
    fun observeDaily(): Flow<List<DailyTemperatureEntity>>
    suspend fun storedDaily(): List<DailyTemperatureEntity>
    suspend fun storeDailyIfCurrent(temperature: DailyTemperatureEntity): Boolean
    suspend fun pruneObsolete()
    suspend fun deleteAll()
}

class RoomTemperatureRepository(private val database: ReadingDatabase) : TemperatureRepository {
    private val dao = database.temperatures()
    override fun observe() = dao.observeAll()
    override suspend fun stored() = dao.getAll()
    override fun observeDaily() = dao.observeAllDaily()
    override suspend fun storedDaily() = dao.getAllDaily()
    override suspend fun storeIfCurrent(temperature: IntervalTemperatureEntity): Boolean = database.withTransaction {
        val intervals = readingIntervals(database.readings().getAll().map { it.toReading() })
        if (intervals.none { it.matches(temperature) }) return@withTransaction false
        java.math.BigDecimal(temperature.celsius)
        dao.upsert(temperature)
        true
    }
    override suspend fun storeDailyIfCurrent(temperature: DailyTemperatureEntity): Boolean = database.withTransaction {
        val date = java.time.LocalDate.parse(temperature.date)
        val covered = readingIntervals(database.readings().getAll().map { it.toReading() }).any {
            date > it.earlier.date && date <= it.later.date
        }
        if (!covered) return@withTransaction false
        java.math.BigDecimal(temperature.celsius)
        dao.upsertDaily(temperature)
        true
    }

    override suspend fun pruneObsolete() = database.withTransaction {
        dao.pruneObsoleteIntervals()
        dao.pruneObsoleteDaily()
    }

    override suspend fun deleteAll() = database.withTransaction {
        dao.deleteAll()
        dao.deleteAllDaily()
    }
}
