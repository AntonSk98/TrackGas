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

@Dao
interface TemperatureDao {
    @Query("SELECT * FROM interval_temperatures")
    fun observeAll(): Flow<List<IntervalTemperatureEntity>>

    @Query("SELECT * FROM interval_temperatures")
    suspend fun getAll(): List<IntervalTemperatureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(temperature: IntervalTemperatureEntity)

    @Query("""DELETE FROM interval_temperatures WHERE NOT EXISTS (
        SELECT 1 FROM readings a JOIN readings b ON a.installationId = b.installationId
        WHERE a.id = earlierId AND b.id = laterId
        AND a.installationId = interval_temperatures.installationId
        AND a.observedAt = startEpoch AND b.observedAt = endEpoch AND a.date < b.date
        AND NOT EXISTS (SELECT 1 FROM readings c WHERE c.installationId = a.installationId
            AND c.date > a.date AND c.date < b.date))""")
    suspend fun pruneObsoleteIntervals()

    @Query("DELETE FROM interval_temperatures")
    suspend fun deleteAll()
}

interface TemperatureRepository {
    fun observe(): Flow<List<IntervalTemperatureEntity>>
    suspend fun stored(): List<IntervalTemperatureEntity>
    suspend fun storeIfCurrent(temperature: IntervalTemperatureEntity): Boolean
    suspend fun deleteAll()
}

class RoomTemperatureRepository(private val database: ReadingDatabase) : TemperatureRepository {
    private val dao = database.temperatures()
    override fun observe() = dao.observeAll()
    override suspend fun stored() = dao.getAll()
    override suspend fun storeIfCurrent(temperature: IntervalTemperatureEntity): Boolean = database.withTransaction {
        val intervals = readingIntervals(database.readings().getAll().map { it.toReading() })
        if (intervals.none { it.matches(temperature) }) return@withTransaction false
        java.math.BigDecimal(temperature.celsius)
        dao.upsert(temperature)
        true
    }
    override suspend fun deleteAll() = dao.deleteAll()
}
