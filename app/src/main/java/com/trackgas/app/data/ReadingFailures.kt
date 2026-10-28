package com.trackgas.app.data

import android.database.SQLException
import com.trackgas.app.tracking.ReadingFailureCategory
import com.trackgas.app.tracking.ReadingStorageException
import java.io.IOException
import kotlinx.coroutines.CancellationException

internal class StoredReadingDecodingException(cause: Throwable) : Exception("Stored reading decoding failed", cause)

/** The same allowlist applies to Flow and suspend boundaries; defects are unchanged. */
internal fun classifyReadingFailure(error: Throwable): Throwable = when (error) {
    is CancellationException -> error
    is StoredReadingDecodingException -> ReadingStorageException(error, ReadingFailureCategory.STORED_DATA)
    is SQLException, is IOException -> ReadingStorageException(error, ReadingFailureCategory.STORAGE_ACCESS)
    else -> error
}

internal suspend fun <T> readingStorageOperation(block: suspend () -> T): T = try {
    block()
} catch (error: SQLException) {
    throw classifyReadingFailure(error)
} catch (error: IOException) {
    throw classifyReadingFailure(error)
} catch (error: StoredReadingDecodingException) {
    throw classifyReadingFailure(error)
}
