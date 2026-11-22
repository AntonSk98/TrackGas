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

    override val usesPeriodScopedDashboardData: Boolean = true

    override fun observeReadings(): Flow<CommittedSnapshot> =
        dao.observeAll().map {
            // Room payloads are only invalidation triggers: buffered rows may predate a commit.
            ordering.withLock { snapshot(database.withTransaction { readRows() }) }
        }.catch { failure -> throw classifyReadingFailure(failure) }

    override fun observeDashboardData(range: DateRange): Flow<DashboardSnapshot> =
        dao.observeCount().map { DashboardSnapshot(loadDashboardData(range)) }
            .catch { failure -> throw classifyReadingFailure(failure) }

    private suspend fun loadDashboardData(range: DateRange): TrackerData = readingStorageOperation {
        database.withTransaction {
            val readings = (
                dao.getReadingsInRange(range.start.toString(), range.endInclusive.toString()) +
                    dao.getReadingsBefore(range.start.toString()) +
                    dao.getReadingsAfter(range.endInclusive.toString())
                ).distinctBy { it.id }.map { it.toReading() }
            validatedTracker(readings, dao.getInstallations().map { it.toInstallation() })
        }
    }

    override suspend fun historyPage(cursor: HistoryCursor?, pageSize: Int): HistoryPage = readingStorageOperation {
        require(pageSize > 0)
        database.withTransaction {
            val rows = dao.getHistoryPage(
                cursorDate = cursor?.date?.toString(),
                cursorStartDatePresent = if (cursor?.installationStartDate != null) 0 else 1,
                cursorStartDate = cursor?.installationStartDate?.toString(),
                cursorInstallationId = cursor?.installationId,
                cursorReadingId = cursor?.readingId,
                limit = pageSize + 1,
            )
            val hasMore = rows.size > pageSize
            val pageRows = rows.take(pageSize).map { it.toReading() }
            val installations = dao.getInstallations().map { it.toInstallation() }
            val installationIds = pageRows.map { it.installationId }.toSet()
            val predecessors = pageRows.minOfOrNull { it.date }?.let { oldestDate ->
                dao.getHistoryPredecessors(oldestDate.toString())
                    .asSequence()
                    .filter { it.installationId in installationIds }
                    .map { it.toReading() }
                    .toList()
            }.orEmpty()
            val startingIds = if (installationIds.isEmpty()) emptySet() else
                dao.getHistoryStartingIds(installationIds.toList()).toSet()
            val last = pageRows.lastOrNull()
            HistoryPage(
                readings = pageRows,
                predecessorReadings = predecessors,
                installations = installations,
                startingReadingIds = startingIds,
                nextCursor = if (hasMore && last != null) historyCursor(last, installations) else null,
            )
        }
    }

    override suspend fun completeTracker(): TrackerData = readingStorageOperation {
        database.withTransaction { readRows() }
    }

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
                    (validation as ReadingValidation.Accepted).createdInstallation?.let { installation ->
                        dao.insertInstallation(InstallationEntity(installation.id, installation.startDate?.toString()))
                    }
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
                val installationId = newIdentity()
                when (val validation = evaluateReplacement(tracker, intent, installationId)) {
                    is ReplacementValidation.Rejected -> ReplaceRows.Rejected(validation.error)
                    is ReplacementValidation.Accepted -> {
                        val next = validation.tracker
                        val installation = next.installations.single { it.id == installationId }
                        val startingReading = next.readings.single { it.installationId == installationId }
                        dao.insertInstallation(InstallationEntity(installation.id, installation.startDate?.toString()))
                        dao.insert(startingReading.toEntity())
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
                val tracker = readRows()
                when (evaluateDeletion(tracker, id)) {
                    DeletionValidation.MISSING -> DeleteRows.Missing
                    DeletionValidation.ALLOWED -> {
                        val deleted = tracker.readings.single { it.id == id }
                        check(dao.delete(id) == 1) { "Reading disappeared during delete" }
                        if (tracker.readings.count { it.installationId == deleted.installationId } == 1) {
                            dao.deleteInstallationsById(listOf(deleted.installationId))
                        }
                        DeleteRows.Accepted(readRows())
                    }
                }
            }
            when (result) {
                DeleteRows.Missing -> DeleteReadingResult.Missing
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
                dao.deleteReadings()
                database.temperatures().deleteAll()
                dao.deleteInstallations()
                readRows()
            }
            mutationRevision++
            snapshot(tracker)
        }
    }

    private suspend fun readRows(): TrackerData {
        return validatedTracker(dao.getAll().map { it.toReading() },
            dao.getInstallations().map { it.toInstallation() })
    }

    private fun validatedTracker(readings: List<Reading>, installations: List<MeterInstallation>): TrackerData {
        val tracker = TrackerData(readings, installations)
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
        data class Accepted(val tracker: TrackerData) : DeleteRows
    }

    private sealed interface ImportRows {
        data object Stale : ImportRows
        data class Rejected(val error: CsvImportError) : ImportRows
        data class Accepted(val tracker: TrackerData) : ImportRows
    }
}
