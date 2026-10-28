package com.trackgas.app.data

import androidx.room.withTransaction
import com.trackgas.app.tracking.*
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomReadingRepository internal constructor(
    private val database: ReadingDatabase,
    private val dao: ReadingDao,
    private val newIdentity: () -> String,
) : ReadingRepository {
    constructor(
        database: ReadingDatabase,
        newIdentity: () -> String = { UUID.randomUUID().toString() },
    ) : this(database, database.readings(), newIdentity)

    private val ordering = Mutex()
    private var revision = 0L

    override fun observeReadings(): Flow<CommittedSnapshot> =
        dao.observeAll().map {
            // Room payloads are only invalidation triggers: buffered rows may predate a commit.
            ordering.withLock { snapshot(readRows()) }
        }.catch { failure -> throw classifyReadingFailure(failure) }

    override suspend fun save(intent: SaveReadingIntent): SaveReadingResult = readingStorageOperation {
        ordering.withLock {
            val result = database.withTransaction {
                val readings = readRows()
                val validation = when (intent) {
                    is SaveReadingIntent.Add -> evaluateSave(readings, intent, newIdentity())
                    is SaveReadingIntent.Edit -> evaluateSave(readings, intent)
                }
                val candidate = when (validation) {
                    is ReadingValidation.Accepted -> validation.candidate
                    is ReadingValidation.Rejected -> return@withTransaction SaveRows.Invalid(validation.errors)
                }
                if (intent is SaveReadingIntent.Add) {
                    dao.insert(candidate.toEntity())
                } else {
                    check(dao.update(candidate.toEntity()) == 1) { "Reading disappeared during update" }
                }
                SaveRows.Accepted(readRows())
            }
            // withTransaction has returned successfully; only now issue a committed revision.
            when (result) {
                is SaveRows.Invalid -> SaveReadingResult.Invalid(result.errors)
                is SaveRows.Accepted -> SaveReadingResult.Saved(snapshot(result.readings))
            }
        }
    }

    override suspend fun delete(id: String): DeleteReadingResult = readingStorageOperation {
        ordering.withLock {
            val rows = database.withTransaction {
                readRows()
                if (dao.delete(id) == 0) null else readRows()
            }
            if (rows == null) DeleteReadingResult.Missing else DeleteReadingResult.Deleted(snapshot(rows))
        }
    }

    private suspend fun readRows(): List<Reading> {
        val readings = dao.getAll().map { it.toReading() }
        readingCollectionViolation(readings)?.let {
            throw StoredReadingDecodingException(IllegalArgumentException("Invalid stored reading collection: $it"))
        }
        return readings
    }

    private fun snapshot(readings: List<Reading>) = CommittedSnapshot(++revision, readings)

    private sealed interface SaveRows {
        data class Invalid(val errors: ReadingErrors) : SaveRows
        data class Accepted(val readings: List<Reading>) : SaveRows
    }
}
