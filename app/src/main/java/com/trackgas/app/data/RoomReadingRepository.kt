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
    private var mutationRevision = 0L
    private val consumedImportPreviews = mutableSetOf<String>()

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
                is SaveRows.Accepted -> {
                    mutationRevision++
                    SaveReadingResult.Saved(snapshot(result.readings))
                }
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
                is ReplaceRows.Accepted -> {
                    mutationRevision++
                    ReplaceMeterResult.Replaced(snapshot(result.tracker))
                }
            }
        }
    }

    override suspend fun previewCsvImport(text: String): CsvImportPreviewResult = readingStorageOperation {
        ordering.withLock {
            val base = database.withTransaction { readRows() }
            val baseSnapshot = snapshot(base)
            when (val validation = validateReadingCsv(text, base.installations.map { it.id }.toSet())) {
                is CsvReadingValidation.Invalid -> CsvImportPreviewResult.Rejected(validation.error)
                is CsvReadingValidation.Valid -> {
                    var previewId = 0
                    val candidate = validateCsvImportCandidate(base, validation.rows) { "csv-preview-${previewId++}" }
                    when (candidate) {
                        is CsvImportCandidateValidation.Invalid -> CsvImportPreviewResult.Rejected(candidate.error)
                        is CsvImportCandidateValidation.Valid -> {
                            val included = validation.rows.map { it.installationId }.toSet()
                            val changes = included.sorted().map { installationId ->
                                CsvImportMeterChange(
                                    installationId = installationId,
                                    beforeReadingCount = base.readings.count { it.installationId == installationId },
                                    afterReadingCount = candidate.tracker.readings.count { it.installationId == installationId },
                                )
                            }
                            CsvImportPreviewResult.Ready(
                                CsvImportPreview(
                                    UUID.randomUUID().toString(), baseSnapshot.revision, changes,
                                    mutationRevision, base, validation.rows,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun confirmCsvImport(preview: CsvImportPreview): CsvImportCommitResult = readingStorageOperation {
        ordering.withLock {
            val result = database.withTransaction {
                val current = readRows()
                if (preview.previewId in consumedImportPreviews ||
                    preview.baseMutationRevision != mutationRevision || current != preview.baseTracker
                ) {
                    ImportRows.Stale
                } else {
                    val usedIds = current.readings.mapTo(mutableSetOf()) { it.id }
                    val candidate = validateCsvImportCandidate(current, preview.rows) {
                        var id: String
                        do { id = newIdentity() } while (id.isBlank() || !usedIds.add(id))
                        id
                    }
                    when (candidate) {
                        is CsvImportCandidateValidation.Invalid -> ImportRows.Rejected(candidate.error)
                        is CsvImportCandidateValidation.Valid -> {
                        val affected = preview.rows.map { it.installationId }.toSet()
                        if (affected.isNotEmpty()) {
                            // Remove links first, then rebuild only the imported meter records.
                            dao.deleteReplacementsForInstallations(affected.toList())
                            dao.deleteForInstallations(affected.toList())
                            dao.deleteInstallationsById(affected.toList())
                            candidate.tracker.installations.filter { it.id in affected }.forEach { installation ->
                                dao.insertInstallation(InstallationEntity(
                                    installation.id, installation.startDate?.toString(),
                                ))
                            }
                            candidate.tracker.readings.filter { it.installationId in affected }.forEach { dao.insert(it.toEntity()) }
                        }
                            ImportRows.Accepted(readRows())
                        }
                    }
                }
            }
            when (result) {
                ImportRows.Stale -> CsvImportCommitResult.StalePreview
                is ImportRows.Rejected -> CsvImportCommitResult.Rejected(result.error)
                is ImportRows.Accepted -> {
                    consumedImportPreviews += preview.previewId
                    mutationRevision++
                    CsvImportCommitResult.Committed(snapshot(result.tracker))
                }
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
                is DeleteRows.Accepted -> {
                    mutationRevision++
                    DeleteReadingResult.Deleted(snapshot(result.tracker))
                }
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
            mutationRevision++
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

    private sealed interface ImportRows {
        data object Stale : ImportRows
        data class Rejected(val error: CsvImportError) : ImportRows
        data class Accepted(val tracker: TrackerData) : ImportRows
    }
}
