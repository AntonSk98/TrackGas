package com.trackgas.app.data

import androidx.room.Database
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import com.trackgas.app.tracking.*
import com.trackgas.app.temperature.IntervalTemperatureEntity
import com.trackgas.app.temperature.TemperatureDao
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "installations")
data class InstallationEntity(@PrimaryKey val id: String, val startDate: String? = null)

@Entity(
    tableName = "readings",
    indices = [Index(value = ["installationId", "date"], unique = true)],
    foreignKeys = [ForeignKey(entity = InstallationEntity::class, parentColumns = ["id"],
        childColumns = ["installationId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT)],
)
data class ReadingEntity(
    @PrimaryKey val id: String,
    val date: String,
    val cubicMetres: String,
    val installationId: String,
    val note: String? = null,
    val observedAt: Long,
    val timezone: String,
)

private fun storedDate(date: String): LocalDate = try {
    LocalDate.parse(date)
} catch (error: DateTimeParseException) {
    throw StoredReadingDecodingException(error)
}

private fun storedIdentity(id: String): String {
    if (id.isBlank()) throw StoredReadingDecodingException(IllegalArgumentException("Invalid stored identity"))
    return id
}

internal fun InstallationEntity.toInstallation() = MeterInstallation(storedIdentity(id), startDate?.let(::storedDate))
internal fun ReadingEntity.toReading(): Reading {
    if (id.isBlank()) throw StoredReadingDecodingException(IllegalArgumentException("Invalid stored identity"))
    val parsedDate = try {
        LocalDate.parse(date)
    } catch (error: DateTimeParseException) {
        throw StoredReadingDecodingException(error)
    }
    val value = try {
        BigDecimal(cubicMetres)
    } catch (error: NumberFormatException) {
        throw StoredReadingDecodingException(error)
    }
    if (value.signum() < 0) throw StoredReadingDecodingException(IllegalArgumentException("Invalid stored meter value"))
    return try {
        Reading(id, parsedDate, value, storedIdentity(installationId), note?.takeUnless { it.isBlank() },
            java.time.Instant.ofEpochSecond(observedAt), java.time.ZoneId.of(timezone))
    } catch (error: java.time.DateTimeException) {
        throw StoredReadingDecodingException(error)
    } catch (error: IllegalArgumentException) {
        throw StoredReadingDecodingException(error)
    }
}

internal fun Reading.toEntity(): ReadingEntity =
    ReadingEntity(id, date.toString(), cubicMetres.toString(), installationId, note?.takeUnless { it.isBlank() },
        observedAt.epochSecond, zone.id)

@Dao
interface ReadingDao {
    @Query("SELECT readings.* FROM readings LEFT JOIN installations ON readings.installationId = installations.id ORDER BY readings.date DESC, installations.startDate DESC, readings.id")
    fun observeAll(): Flow<List<ReadingEntity>>

    @Query("SELECT readings.* FROM readings LEFT JOIN installations ON readings.installationId = installations.id ORDER BY readings.date DESC, installations.startDate DESC, readings.id")
    suspend fun getAll(): List<ReadingEntity>

    @Query("SELECT MIN(date) AS startDate, MAX(date) AS endDate FROM readings")
    suspend fun dateRange(): StoredReadingDateRange

    @Query("SELECT COUNT(*) FROM readings")
    suspend fun count(): Int

    @Query("SELECT * FROM installations ORDER BY startDate, id")
    suspend fun getInstallations(): List<InstallationEntity>

    @Insert
    suspend fun insertInstallation(installation: InstallationEntity)

    @Insert
    suspend fun insert(reading: ReadingEntity)

    @Update
    suspend fun update(reading: ReadingEntity): Int

    @Query("DELETE FROM readings WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("DELETE FROM readings WHERE installationId IN (:installationIds)")
    suspend fun deleteForInstallations(installationIds: List<String>)

    @Query("DELETE FROM installations WHERE id IN (:installationIds)")
    suspend fun deleteInstallationsById(installationIds: List<String>)

    @Query("DELETE FROM readings")
    suspend fun deleteReadings()

    @Query("DELETE FROM installations")
    suspend fun deleteInstallations()
}

data class StoredReadingDateRange(val startDate: String?, val endDate: String?)

@Database(
    entities = [ReadingEntity::class, InstallationEntity::class, IntervalTemperatureEntity::class],
    version = 6,
    exportSchema = true,
)
abstract class ReadingDatabase : RoomDatabase() {
    abstract fun readings(): ReadingDao
    abstract fun temperatures(): TemperatureDao

    companion object {
        val INITIALIZE = object : Callback() {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("INSERT INTO installations (id, startDate) VALUES (?, NULL)", arrayOf(UUID.randomUUID().toString()))
            }
        }
    }
}
