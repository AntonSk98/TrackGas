package com.trackgas.app.data

import androidx.room.Database
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import com.trackgas.app.tracking.Reading
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "readings", indices = [Index(value = ["date"], unique = true)])
data class ReadingEntity(
    @PrimaryKey val id: String,
    val date: String,
    val cubicMetres: String,
)

internal fun ReadingEntity.toReading(): Reading =
    Reading(id, LocalDate.parse(date), BigDecimal(cubicMetres))

internal fun Reading.toEntity(): ReadingEntity =
    ReadingEntity(id, date.toString(), cubicMetres.toString())

@Dao
interface ReadingDao {
    @Query("SELECT * FROM readings ORDER BY date DESC")
    fun observeAll(): Flow<List<ReadingEntity>>

    @Query("SELECT * FROM readings ORDER BY date DESC")
    suspend fun getAll(): List<ReadingEntity>

    @Insert
    suspend fun insert(reading: ReadingEntity)

    @Update
    suspend fun update(reading: ReadingEntity): Int

    @Query("DELETE FROM readings WHERE id = :id")
    suspend fun delete(id: String): Int
}

@Database(entities = [ReadingEntity::class], version = 1, exportSchema = true)
abstract class ReadingDatabase : RoomDatabase() {
    abstract fun readings(): ReadingDao
}
