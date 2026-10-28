package com.trackgas.app

import com.trackgas.app.data.*
import com.trackgas.app.tracking.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ReadingFailuresTest {
    @Test fun classificationPreservesCausesAndDoesNotTranslateDefectsOrCancellation() = runTest {
        val io = IOException("private SQL and values")
        val wrapped = classifyReadingFailure(io) as ReadingStorageException
        assertSame(io, wrapped.cause)
        assertEquals(ReadingFailureCategory.STORAGE_ACCESS, wrapped.category)
        assertEquals("Reading storage failed", wrapped.message)
        val decoder = StoredReadingDecodingException(NumberFormatException("private"))
        val stored = classifyReadingFailure(decoder) as ReadingStorageException
        assertSame(decoder, stored.cause)
        assertEquals(ReadingFailureCategory.STORED_DATA, stored.category)
        for (error in listOf(CancellationException("cancel"), IllegalArgumentException("defect"), IllegalStateException("invariant"))) {
            assertSame(error, classifyReadingFailure(error))
            try {
                readingStorageOperation<Unit> { throw error }
                fail("Expected original exception")
            } catch (actual: Exception) {
                assertSame(error, actual)
            }
        }
        try {
            readingStorageOperation<Unit> { throw io }
            fail("Expected classified exception")
        } catch (actual: ReadingStorageException) {
            assertSame(io, actual.cause)
        }
    }

    @Test fun diagnosticValueHasOnlySafeCategoryAndAction() {
        val failure = ReadingFailure(ReadingFailureCategory.STORED_DATA, ReadingAction.SAVE)
        assertEquals("ReadingFailure(category=STORED_DATA, action=SAVE)", failure.toString())
        assertEquals(setOf("category", "action"), ReadingFailure::class.java.declaredFields.filterNot {
            it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers)
        }.map { it.name }.toSet())
    }
}
