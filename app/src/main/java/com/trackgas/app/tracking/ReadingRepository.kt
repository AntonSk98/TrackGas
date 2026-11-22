package com.trackgas.app.tracking

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

enum class ReadingFailureCategory { STORAGE_ACCESS, STORED_DATA }
enum class ReadingAction { LOAD, SAVE, DELETE, REPLACE, PURGE }
data class ReadingFailure(val category: ReadingFailureCategory, val action: ReadingAction)

class ReadingStorageException(
    cause: Throwable,
    val category: ReadingFailureCategory = ReadingFailureCategory.STORAGE_ACCESS,
) : Exception("Reading storage failed", cause)

sealed interface SaveReadingResult {
    data class Saved(val snapshot: CommittedSnapshot) : SaveReadingResult
    data class Invalid(val errors: ReadingErrors) : SaveReadingResult
}

sealed interface DeleteReadingResult {
    data class Deleted(val snapshot: CommittedSnapshot) : DeleteReadingResult
    data object Missing : DeleteReadingResult
}

sealed interface ReplaceMeterResult {
    data class Replaced(val snapshot: CommittedSnapshot) : ReplaceMeterResult
    data class Rejected(val error: ReplacementError) : ReplaceMeterResult
}

/**
 * Full committed tracker with independent installation-local chronology.
 *
 * [revision] orders captures and commits within one repository instance, not across processes or
 * instances. It is not a database version or a reading count. Row order is unspecified; exact
 * decimal scale remains significant. Implementations must keep [readings] stable after delivery,
 * and consumers must not mutate it.
 */
data class CommittedSnapshot(val revision: Long, val tracker: TrackerData) {
    constructor(revision: Long, readings: List<Reading>) : this(revision, TrackerData(readings))
    val readings: List<Reading> get() = tracker.readings
}

data class HistoryCursor(
    val date: LocalDate,
    val installationStartDate: LocalDate?,
    val installationId: String,
    val readingId: String,
)

data class HistoryPage(
    val readings: List<Reading>,
    val predecessorReadings: List<Reading>,
    val installations: List<MeterInstallation>,
    val startingReadingIds: Set<String>,
    val nextCursor: HistoryCursor?,
)

data class DashboardSnapshot(val tracker: TrackerData, val revision: Long? = null)

const val HISTORY_PAGE_SIZE = 50

internal fun dashboardDataForPeriod(tracker: TrackerData, range: DateRange): TrackerData {
    val readings = tracker.readings.groupBy { it.installationId }.values.flatMap { installation ->
        val ordered = installation.sortedBy { it.date }
        ordered.filter { it.date in range.start..range.endInclusive } +
            listOfNotNull(
                ordered.lastOrNull { it.date < range.start },
                ordered.firstOrNull { it.date > range.endInclusive },
            )
    }
    return TrackerData(readings.distinctBy { it.id }, tracker.installations)
}

internal fun historyCursor(reading: Reading, installations: List<MeterInstallation>) = HistoryCursor(
    reading.date,
    installations.single { it.id == reading.installationId }.startDate,
    reading.installationId,
    reading.id,
)

internal fun historyOrder(installations: List<MeterInstallation>): Comparator<Reading> {
    val installationOrder = installations
        .sortedWith(compareBy<MeterInstallation> { it.startDate != null }.thenBy { it.startDate }.thenBy { it.id })
        .mapIndexed { index, installation -> installation.id to index }
        .toMap()
    return compareByDescending<Reading> { it.date }
        .thenByDescending { installationOrder.getValue(it.installationId) }
        .thenBy { it.id }
}

internal fun historyPageFromTracker(
    tracker: TrackerData,
    cursor: HistoryCursor?,
    pageSize: Int,
): HistoryPage {
    require(pageSize > 0)
    val ordered = tracker.readings.sortedWith(historyOrder(tracker.installations))
    val installationComparator = compareBy<MeterInstallation> { it.startDate != null }
        .thenBy { it.startDate }
        .thenBy { it.id }
    val cursorInstallation = cursor?.let { MeterInstallation(it.installationId, it.installationStartDate) }
    val remaining = if (cursor == null || cursorInstallation == null) ordered else ordered.filter { reading ->
        when {
            reading.date < cursor.date -> true
            reading.date > cursor.date -> false
            else -> {
                val installation = tracker.installations.single { it.id == reading.installationId }
                val comparison = installationComparator.compare(installation, cursorInstallation)
                comparison < 0 || (comparison == 0 && reading.id > cursor.readingId)
            }
        }
    }
    val page = remaining.take(pageSize + 1)
    val hasMore = page.size > pageSize
    val readings = page.take(pageSize)
    val oldestDate = readings.minOfOrNull { it.date }
    val pageInstallations = readings.map { it.installationId }.toSet()
    val predecessors = if (oldestDate == null) emptyList() else tracker.readings
        .filter { it.installationId in pageInstallations && it.date < oldestDate }
        .groupBy { it.installationId }
        .values.mapNotNull { rows -> rows.maxByOrNull { it.date } }
    val startingIds = tracker.installations.filter { it.id in pageInstallations }
        .mapNotNull { installation ->
            installation.startDate?.let { date ->
                tracker.readings.firstOrNull { it.installationId == installation.id && it.date == date }?.id
            }
        }.toSet()
    val last = readings.lastOrNull()
    return HistoryPage(
        readings = readings,
        predecessorReadings = predecessors,
        installations = tracker.installations,
        startingReadingIds = startingIds,
        nextCursor = if (hasMore && last != null) historyCursor(last, tracker.installations) else null,
    )
}

data class CsvImportMeterChange(
    val installationId: String,
    val beforeReadingCount: Int,
    val afterReadingCount: Int,
)

/** An immutable, validated replacement staged against one committed tracker snapshot. */
data class CsvImportPreview internal constructor(
    val previewId: String,
    val baseRevision: Long,
    val changes: List<CsvImportMeterChange>,
    internal val baseMutationRevision: Long,
    internal val baseTracker: TrackerData,
    internal val rows: List<CsvReadingRow>,
)

sealed interface CsvImportPreviewResult {
    data class Ready(val preview: CsvImportPreview) : CsvImportPreviewResult
    data class Rejected(val error: CsvImportError) : CsvImportPreviewResult
}

sealed interface CsvImportCommitResult {
    data class Committed(val snapshot: CommittedSnapshot) : CsvImportCommitResult
    data class Rejected(val error: CsvImportError) : CsvImportCommitResult
    data object StalePreview : CsvImportCommitResult
}

/**
 * Transactional reading storage with one process-local revision domain shared by all consumers.
 *
 * Observations and successful mutations allocate strictly increasing revisions in capture/commit
 * order. A capture preceding a commit cannot receive a later revision than that commit, even if
 * delivery is delayed. Resubscribing does not reset revisions; replacing this repository beneath
 * a live consumer's revision watermark is unsupported.
 *
 * Expected storage-access or invalid persisted-data failures throw [ReadingStorageException] with
 * the corresponding [ReadingFailureCategory]. Cancellation and unrelated programming/invariant
 * failures propagate unchanged. Retry never repairs, deletes, or resets invalid stored records.
 * Cancellation before a mutation commits rolls back; cancellation after commit does not undo the
 * write, even if its result is not delivered. Observation or reload can recover committed state.
 */
interface ReadingRepository {
    /**
     * Captures current committed, validated readings on subscription and subsequent invalidation.
     *
     * May repeat identical readings or skip intermediate states, but converges to current committed
     * data while collection remains active and healthy. A buffered old payload must not be stamped
     * with a revision newer than a later captured commit. Delivery may lag capture; consumers can
     * reject stale revisions across this stream and mutation results.
     */
    fun observeReadings(): Flow<CommittedSnapshot>

    /** True when dashboard observations return only period-scoped readings. */
    val usesPeriodScopedDashboardData: Boolean get() = false

    /** Emits consistent dashboard inputs when readings change. */
    fun observeDashboardData(range: DateRange): Flow<DashboardSnapshot> = flow {
        var latestRevision = Long.MIN_VALUE
        observeReadings().collect { snapshot ->
            if (snapshot.revision >= latestRevision) {
                latestRevision = snapshot.revision
                emit(DashboardSnapshot(snapshot.tracker, snapshot.revision))
            }
        }
    }

    /** Loads a stable newest-first page and its limited calculation context. */
    suspend fun historyPage(cursor: HistoryCursor?, pageSize: Int = HISTORY_PAGE_SIZE): HistoryPage =
        historyPageFromTracker(observeReadings().first().tracker, cursor, pageSize)

    /** Loads a complete committed tracker only for operations that require full history. */
    suspend fun completeTracker(): TrackerData = observeReadings().first().tracker

    /**
     * Validates against the latest persisted rows and writes atomically, retaining Edit identity/date.
     *
     * Only a committed write returns [SaveReadingResult.Saved], with its full committed snapshot.
     * Invalid input writes nothing and returns field errors. Invalid/missing/failed operations do
     * not issue successful mutation snapshots. Add identity generation belongs to this boundary.
     */
    suspend fun save(intent: SaveReadingIntent): SaveReadingResult

    /**
     * Validates persisted rows, resolves [id], and deletes atomically.
     *
     * Only a committed deletion returns [DeleteReadingResult.Deleted] and a full committed snapshot.
     * An absent target returns [DeleteReadingResult.Missing] without a successful mutation snapshot.
     */
    suspend fun delete(id: String): DeleteReadingResult

    /*    * Atomically revalidates the current installation and creates a new meter with its starting reading. */
    suspend fun replaceMeter(intent: ReplaceMeterIntent): ReplaceMeterResult

    /** Parses and validates an entire CSV against a consistent committed tracker snapshot. */
    suspend fun previewCsvImport(text: String): CsvImportPreviewResult = CsvImportPreviewResult.Rejected(
        CsvImportError(CsvImportErrorCode.CSV_IMPORT_FAILED),
    )

    /** Revalidates a preview against current data and atomically replaces its included histories. */
    suspend fun confirmCsvImport(preview: CsvImportPreview): CsvImportCommitResult = CsvImportCommitResult.Rejected(
        CsvImportError(CsvImportErrorCode.CSV_IMPORT_FAILED),
    )

    /** Removes all tracking history atomically and returns a tracker with no meters. */
    suspend fun purge(): CommittedSnapshot
}
