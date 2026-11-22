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
import com.trackgas.app.temperature.DailyTemperatureEntity
import com.trackgas.app.temperature.IntervalTemperatureEntity
import com.trackgas.app.temperature.TemperatureDao
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "installations")
data class InstallationEntity(@PrimaryKey val id: String, val startDate: String? = null)

@Entity(
    tableName = "readings",
    indices = [
        Index(value = ["installationId", "date"], unique = true),
        Index(value = ["date"]),
    ],
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

    @Query("SELECT COUNT(*) FROM readings")
    fun observeCount(): Flow<Int>

    @Query("SELECT readings.* FROM readings LEFT JOIN installations ON readings.installationId = installations.id ORDER BY readings.date DESC, installations.startDate DESC, readings.id")
    suspend fun getAll(): List<ReadingEntity>

    @Query("SELECT * FROM installations ORDER BY startDate, id")
    suspend fun getInstallations(): List<InstallationEntity>

    @Query("SELECT * FROM readings WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getReadingsInRange(startDate: String, endDate: String): List<ReadingEntity>

    @Query("""
        SELECT readings.* FROM readings
        JOIN (
            SELECT installationId, MAX(date) AS boundaryDate
            FROM readings WHERE date < :startDate GROUP BY installationId
        ) boundary ON readings.installationId = boundary.installationId AND readings.date = boundary.boundaryDate
    """)
    suspend fun getReadingsBefore(startDate: String): List<ReadingEntity>

    @Query("""
        SELECT readings.* FROM readings
        JOIN (
            SELECT installationId, MIN(date) AS boundaryDate
            FROM readings WHERE date > :endDate GROUP BY installationId
        ) boundary ON readings.installationId = boundary.installationId AND readings.date = boundary.boundaryDate
    """)
    suspend fun getReadingsAfter(endDate: String): List<ReadingEntity>

    @Query("""
        SELECT readings.* FROM readings
        LEFT JOIN installations ON readings.installationId = installations.id
        WHERE :cursorDate IS NULL OR readings.date < :cursorDate OR (
            readings.date = :cursorDate AND (
                CASE WHEN installations.startDate IS NULL THEN 1 ELSE 0 END > :cursorStartDatePresent
                OR (
                    CASE WHEN installations.startDate IS NULL THEN 1 ELSE 0 END = :cursorStartDatePresent
                    AND (
                        (
                            :cursorStartDatePresent = 0 AND (
                                installations.startDate IS NULL
                                OR installations.startDate < :cursorStartDate
                                OR (
                                    installations.startDate = :cursorStartDate
                                    AND installations.id < :cursorInstallationId
                                )
                            )
                        )
                        OR (:cursorStartDatePresent = 1 AND installations.id < :cursorInstallationId)
                        OR (installations.id = :cursorInstallationId AND readings.id > :cursorReadingId)
                    )
                )
            )
        )
        ORDER BY readings.date DESC,
            CASE WHEN installations.startDate IS NULL THEN 1 ELSE 0 END ASC,
            installations.startDate DESC, installations.id DESC, readings.id ASC
        LIMIT :limit
    """)
    suspend fun getHistoryPage(
        cursorDate: String?,
        cursorStartDatePresent: Int,
        cursorStartDate: String?,
        cursorInstallationId: String?,
        cursorReadingId: String?,
        limit: Int,
    ): List<ReadingEntity>

    @Query("""
        SELECT readings.* FROM readings
        JOIN (
            SELECT installationId, MAX(date) AS boundaryDate
            FROM readings WHERE date < :date GROUP BY installationId
        ) boundary ON readings.installationId = boundary.installationId AND readings.date = boundary.boundaryDate
    """)
    suspend fun getHistoryPredecessors(date: String): List<ReadingEntity>

    @Query("""
        SELECT readings.id FROM readings
        JOIN installations ON readings.installationId = installations.id
        WHERE readings.installationId IN (:installationIds)
            AND installations.startDate IS NOT NULL
            AND readings.date = installations.startDate
    """)
    suspend fun getHistoryStartingIds(installationIds: List<String>): List<String>

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

@Database(
    entities = [ReadingEntity::class, InstallationEntity::class, IntervalTemperatureEntity::class, DailyTemperatureEntity::class],
    version = 9,
    exportSchema = true,
)
abstract class ReadingDatabase : RoomDatabase() {
    abstract fun readings(): ReadingDao
    abstract fun temperatures(): TemperatureDao
}
