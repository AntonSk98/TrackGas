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
            ordering.withLock { snapshot(database.withTransaction { readRows() }) }
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

    override suspend fun replaceMeter(intent: ReplaceMeterIntent): ReplaceMeterResult = readingStorageOperation {
        ordering.withLock {
            val result = database.withTransaction {
                val tracker = readRows()
                when (val validation = evaluateReplacement(tracker, intent, newIdentity(), newIdentity(), newIdentity())) {
                    is ReplacementValidation.Rejected -> ReplaceRows.Rejected(validation.error)
                    is ReplacementValidation.Accepted -> {
                        val next = validation.tracker
                        val installation = next.installations.last()
                        val replacement = next.replacements.last()
                        dao.insertInstallation(InstallationEntity(installation.id, installation.startDate?.toString()))
                        dao.insert(next.readings.last().toEntity())
                        dao.insertReplacement(ReplacementEntity(replacement.id, replacement.date.toString(),
                            replacement.oldInstallationId, replacement.newInstallationId, replacement.finalReadingId, replacement.initialReadingId))
                        ReplaceRows.Accepted(readRows())
                    }
                }
            }
            when (result) {
                is ReplaceRows.Rejected -> ReplaceMeterResult.Rejected(result.error)
                is ReplaceRows.Accepted -> ReplaceMeterResult.Replaced(snapshot(result.tracker))
            }
        }
    }

    override suspend fun delete(id: String): DeleteReadingResult = readingStorageOperation {
        ordering.withLock {
            val result = database.withTransaction {
                when (evaluateDeletion(readRows(), id)) {
                    DeletionValidation.MISSING -> DeleteRows.Missing
                    DeletionValidation.PROTECTED -> DeleteRows.Protected
                    DeletionValidation.ALLOWED -> {
                        check(dao.delete(id) == 1) { "Reading disappeared during delete" }
                        DeleteRows.Accepted(readRows())
                    }
                }
            }
            when (result) {
                DeleteRows.Missing -> DeleteReadingResult.Missing
                DeleteRows.Protected -> DeleteReadingResult.Protected
                is DeleteRows.Accepted -> DeleteReadingResult.Deleted(snapshot(result.tracker))
            }
        }
    }

    override suspend fun purge(): CommittedSnapshot = readingStorageOperation {
        ordering.withLock {
            val tracker = database.withTransaction {
                val before = readRows()
                // Remove dependent rows first and restore the required original installation.
                dao.deleteReplacements()
                dao.deleteReadings()
                dao.deleteInstallations()
                var id: String
                do { id = UUID.randomUUID().toString() } while (before.installations.any { it.id == id })
                dao.insertInstallation(InstallationEntity(id))
                readRows()
            }
            snapshot(tracker)
        }
    }

    private suspend fun readRows(): TrackerData {
        val tracker = TrackerData(dao.getAll().map { it.toReading() },
            dao.getInstallations().map { it.toInstallation() }, dao.getReplacements().map { it.toReplacement() })
        readingCollectionViolation(tracker)?.let {
            throw StoredReadingDecodingException(IllegalArgumentException("Invalid stored reading collection: $it"))
        }
        return tracker
    }

    private fun snapshot(tracker: TrackerData) = CommittedSnapshot(++revision, tracker)

    private sealed interface SaveRows {
        data class Invalid(val errors: ReadingErrors) : SaveRows
        data class Accepted(val readings: TrackerData) : SaveRows
    }

    private sealed interface ReplaceRows {
        data class Rejected(val error: ReplacementError) : ReplaceRows
        data class Accepted(val tracker: TrackerData) : ReplaceRows
    }

    private sealed interface DeleteRows {
        data object Missing : DeleteRows
        data object Protected : DeleteRows
        data class Accepted(val tracker: TrackerData) : DeleteRows
    }
}
