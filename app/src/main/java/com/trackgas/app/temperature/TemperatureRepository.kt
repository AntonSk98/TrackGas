package com.trackgas.app.temperature

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.withTransaction
import com.trackgas.app.data.ReadingDatabase
import com.trackgas.app.tracking.DateRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** Only the completed mean and the exact inputs used to obtain it are durable. */
@Entity(
    tableName = "interval_temperatures",
    primaryKeys = ["earlierId", "laterId"],
    indices = [Index(value = ["location", "laterId"])],
)
data class IntervalTemperatureEntity(
    val earlierId: String,
    val laterId: String,
    val installationId: String,
    val startEpoch: Long,
    val endEpoch: Long,
    val location: String,
    val celsius: String,
)

@Entity(
    tableName = "daily_temperatures",
    primaryKeys = ["date", "location"],
    indices = [Index(value = ["location", "date"])],
)
data class DailyTemperatureEntity(
    val date: String,
    val location: String,
    val celsius: String,
)

@Dao
interface TemperatureDao {
    @Query("SELECT COUNT(*) FROM interval_temperatures")
    fun observeIntervalCount(): Flow<Int>

    @Query("SELECT * FROM interval_temperatures WHERE location = :location AND laterId IN (:readingIds)")
    suspend fun getForLaterReadings(readingIds: List<String>, location: String): List<IntervalTemperatureEntity>

    @Query("SELECT COUNT(*) FROM daily_temperatures")
    fun observeDailyCount(): Flow<Int>

    @Query("SELECT * FROM daily_temperatures WHERE location = :location AND date BETWEEN :startDate AND :endDate")
    suspend fun getDailyInRange(startDate: String, endDate: String, location: String): List<DailyTemperatureEntity>

    @Query("""SELECT EXISTS (
        SELECT 1 FROM readings a JOIN readings b ON a.installationId = b.installationId
        WHERE a.id = :earlierId AND b.id = :laterId
            AND a.installationId = :installationId
            AND a.observedAt = :startEpoch AND b.observedAt = :endEpoch AND a.date < b.date
            AND NOT EXISTS (SELECT 1 FROM readings c WHERE c.installationId = a.installationId
                AND c.date > a.date AND c.date < b.date)
    )""")
    suspend fun isCurrentInterval(
        earlierId: String,
        laterId: String,
        installationId: String,
        startEpoch: Long,
        endEpoch: Long,
    ): Boolean

    @Query("""SELECT EXISTS (
        SELECT 1 FROM readings a JOIN readings b ON a.installationId = b.installationId
        WHERE a.date < b.date
            AND :date > a.date AND :date <= b.date
            AND NOT EXISTS (SELECT 1 FROM readings c WHERE c.installationId = a.installationId
                AND c.date > a.date AND c.date < b.date)
    )""")
    suspend fun isDateCovered(date: String): Boolean

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
    fun observeChanges(): Flow<Unit>
    suspend fun storedForLaterReadings(readingIds: Set<String>, location: String): List<IntervalTemperatureEntity>
    suspend fun storeIfCurrent(temperature: IntervalTemperatureEntity): Boolean
    suspend fun storedDaily(range: DateRange, location: String): List<DailyTemperatureEntity>
    suspend fun storeDailyIfCurrent(temperature: DailyTemperatureEntity): Boolean
    suspend fun pruneObsolete()
    suspend fun deleteAll()
}

class RoomTemperatureRepository(private val database: ReadingDatabase) : TemperatureRepository {
    private val dao = database.temperatures()
    override fun observeChanges() = merge(
        dao.observeIntervalCount().map { Unit },
        dao.observeDailyCount().map { Unit },
    )
    override suspend fun storedForLaterReadings(
        readingIds: Set<String>,
        location: String,
    ): List<IntervalTemperatureEntity> =
        if (readingIds.isEmpty()) emptyList() else dao.getForLaterReadings(readingIds.toList(), location)
    override suspend fun storedDaily(range: DateRange, location: String) =
        dao.getDailyInRange(range.start.toString(), range.endInclusive.toString(), location)
    override suspend fun storeIfCurrent(temperature: IntervalTemperatureEntity): Boolean = database.withTransaction {
        val isCurrent = dao.isCurrentInterval(
            temperature.earlierId,
            temperature.laterId,
            temperature.installationId,
            temperature.startEpoch,
            temperature.endEpoch,
        )
        if (!isCurrent) return@withTransaction false
        java.math.BigDecimal(temperature.celsius)
        dao.upsert(temperature)
        true
    }
    override suspend fun storeDailyIfCurrent(temperature: DailyTemperatureEntity): Boolean = database.withTransaction {
        java.time.LocalDate.parse(temperature.date)
        if (!dao.isDateCovered(temperature.date)) return@withTransaction false
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
