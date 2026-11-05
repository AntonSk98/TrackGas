package com.trackgas.app.temperature

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.trackgas.app.data.StoredReadingDecodingException
import com.trackgas.app.data.ReadingDatabase
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One durable Celsius value for a calendar day. */
/** One durable Celsius value for a calendar day. */
@Entity(tableName = "daily_temperatures")
data class DailyTemperatureEntity(
    @PrimaryKey val date: String,
    val celsius: String,
)

/** Database operations for persisted daily temperature values. */
@Dao
interface TemperatureDao {
    @Query("SELECT * FROM daily_temperatures ORDER BY date")
    fun observeAll(): Flow<List<DailyTemperatureEntity>>

    @Query("SELECT date FROM daily_temperatures")
    suspend fun storedDates(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(temperatures: List<DailyTemperatureEntity>)

    @Query("DELETE FROM daily_temperatures WHERE NOT EXISTS (SELECT 1 FROM readings) OR date < (SELECT MIN(date) FROM readings) OR date > (SELECT MAX(date) FROM readings)")
    suspend fun pruneOutsideReadingRange()

    @Query("DELETE FROM daily_temperatures")
    suspend fun deleteAll()
}

/** Exposes cached daily temperatures without coupling presentation to Room. */
/** Exposes cached daily temperatures without coupling presentation to Room. */
interface TemperatureRepository {
    fun observe(): Flow<Map<LocalDate, BigDecimal>>
    suspend fun storedDates(): Set<LocalDate>
    suspend fun upsert(temperatures: Map<LocalDate, BigDecimal>)
    suspend fun deleteAll()
}

/** Room-backed temperature storage; dates and decimal values retain exact text representations. */
class RoomTemperatureRepository(private val dao: TemperatureDao) : TemperatureRepository {
    constructor(database: ReadingDatabase) : this(database.temperatures())

    override fun observe(): Flow<Map<LocalDate, BigDecimal>> =
        dao.observeAll().map { rows -> rows.associate { it.toTemperature() } }

    override suspend fun storedDates(): Set<LocalDate> = dao.storedDates().mapTo(mutableSetOf(), ::storedDate)

    override suspend fun upsert(temperatures: Map<LocalDate, BigDecimal>) {
        if (temperatures.isEmpty()) return
        dao.upsert(temperatures.map { (date, value) ->
            DailyTemperatureEntity(date.toString(), value.toString())
        })
    }

    override suspend fun deleteAll() = dao.deleteAll()

    private fun DailyTemperatureEntity.toTemperature(): Pair<LocalDate, BigDecimal> {
        val date = storedDate(date)
        val value = try {
            BigDecimal(celsius)
        } catch (error: NumberFormatException) {
            throw StoredReadingDecodingException(error)
        }
        return date to value
    }

    private fun storedDate(value: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (error: DateTimeParseException) {
        throw StoredReadingDecodingException(error)
    }
}
