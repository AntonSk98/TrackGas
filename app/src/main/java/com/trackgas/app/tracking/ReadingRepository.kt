package com.trackgas.app.tracking

import kotlinx.coroutines.flow.Flow

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
    data object Protected : DeleteReadingResult
}

sealed interface ReplaceMeterResult {
    data class Replaced(val snapshot: CommittedSnapshot) : ReplaceMeterResult
    data class Rejected(val error: ReplacementError) : ReplaceMeterResult
}

/**
 * Full committed tracker with installation-local chronology and explicit replacement boundaries.
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

    /** Atomically revalidates the expected final reading and starts the next installation at zero. */
    suspend fun replaceMeter(intent: ReplaceMeterIntent): ReplaceMeterResult

    /** Removes all tracking history atomically and returns the committed empty original installation. */
    suspend fun purge(): CommittedSnapshot
}
