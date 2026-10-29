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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.trackgas.app.tracking.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
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
    val installationId: String = ORIGINAL_INSTALLATION_ID,
)

@Entity(
    tableName = "replacements",
    indices = [Index(value = ["date"], unique = true), Index(value = ["oldInstallationId"], unique = true),
        Index(value = ["newInstallationId"], unique = true), Index(value = ["finalReadingId"], unique = true),
        Index(value = ["initialReadingId"], unique = true)],
    foreignKeys = [
        ForeignKey(entity = InstallationEntity::class, parentColumns = ["id"], childColumns = ["oldInstallationId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT),
        ForeignKey(entity = InstallationEntity::class, parentColumns = ["id"], childColumns = ["newInstallationId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT),
        ForeignKey(entity = ReadingEntity::class, parentColumns = ["id"], childColumns = ["finalReadingId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT),
        ForeignKey(entity = ReadingEntity::class, parentColumns = ["id"], childColumns = ["initialReadingId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT),
    ],
)
data class ReplacementEntity(
    @PrimaryKey val id: String, val date: String, val oldInstallationId: String,
    val newInstallationId: String, val finalReadingId: String, val initialReadingId: String,
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
internal fun ReplacementEntity.toReplacement() = MeterReplacement(
    storedIdentity(id), storedDate(date), storedIdentity(oldInstallationId), storedIdentity(newInstallationId),
    storedIdentity(finalReadingId), storedIdentity(initialReadingId),
)

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
    return Reading(id, parsedDate, value, storedIdentity(installationId))
}

internal fun Reading.toEntity(): ReadingEntity =
    ReadingEntity(id, date.toString(), cubicMetres.toString(), installationId)

@Dao
interface ReadingDao {
    @Query("SELECT readings.* FROM readings LEFT JOIN installations ON readings.installationId = installations.id LEFT JOIN replacements ON readings.id = replacements.initialReadingId ORDER BY readings.date DESC, installations.startDate DESC, readings.id")
    fun observeAll(): Flow<List<ReadingEntity>>

    @Query("SELECT readings.* FROM readings LEFT JOIN installations ON readings.installationId = installations.id ORDER BY readings.date DESC, installations.startDate DESC, readings.id")
    suspend fun getAll(): List<ReadingEntity>

    @Query("SELECT * FROM installations ORDER BY startDate, id")
    suspend fun getInstallations(): List<InstallationEntity>

    @Query("SELECT * FROM replacements ORDER BY date, id")
    suspend fun getReplacements(): List<ReplacementEntity>

    @Insert
    suspend fun insertInstallation(installation: InstallationEntity)

    @Insert
    suspend fun insertReplacement(replacement: ReplacementEntity)

    @Insert
    suspend fun insert(reading: ReadingEntity)

    @Update
    suspend fun update(reading: ReadingEntity): Int

    @Query("DELETE FROM readings WHERE id = :id")
    suspend fun delete(id: String): Int
}

@Database(entities = [ReadingEntity::class, InstallationEntity::class, ReplacementEntity::class], version = 2, exportSchema = true)
abstract class ReadingDatabase : RoomDatabase() {
    abstract fun readings(): ReadingDao

    companion object {
        val INITIALIZE = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("INSERT INTO installations (id, startDate) VALUES ('original', NULL)")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS installations (id TEXT NOT NULL PRIMARY KEY, startDate TEXT)")
                db.execSQL("INSERT INTO installations (id, startDate) VALUES ('original', NULL)")
                db.execSQL("CREATE TABLE readings_new (id TEXT NOT NULL PRIMARY KEY, date TEXT NOT NULL, cubicMetres TEXT NOT NULL, installationId TEXT NOT NULL, FOREIGN KEY(installationId) REFERENCES installations(id) ON UPDATE RESTRICT ON DELETE RESTRICT)")
                db.execSQL("INSERT INTO readings_new (id, date, cubicMetres, installationId) SELECT id, date, cubicMetres, 'original' FROM readings")
                db.execSQL("DROP TABLE readings")
                db.execSQL("ALTER TABLE readings_new RENAME TO readings")
                db.execSQL("CREATE UNIQUE INDEX index_readings_installationId_date ON readings (installationId, date)")
                db.execSQL("CREATE TABLE replacements (id TEXT NOT NULL PRIMARY KEY, date TEXT NOT NULL, oldInstallationId TEXT NOT NULL, newInstallationId TEXT NOT NULL, finalReadingId TEXT NOT NULL, initialReadingId TEXT NOT NULL, FOREIGN KEY(oldInstallationId) REFERENCES installations(id) ON UPDATE RESTRICT ON DELETE RESTRICT, FOREIGN KEY(newInstallationId) REFERENCES installations(id) ON UPDATE RESTRICT ON DELETE RESTRICT, FOREIGN KEY(finalReadingId) REFERENCES readings(id) ON UPDATE RESTRICT ON DELETE RESTRICT, FOREIGN KEY(initialReadingId) REFERENCES readings(id) ON UPDATE RESTRICT ON DELETE RESTRICT)")
                for (column in listOf("date", "oldInstallationId", "newInstallationId", "finalReadingId", "initialReadingId")) {
                    db.execSQL("CREATE UNIQUE INDEX index_replacements_$column ON replacements ($column)")
                }
            }
        }
    }
}
