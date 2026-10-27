package com.trackgas.app.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackgas.app.tracking.Reading
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingDatabaseTest {
    private lateinit var database: ReadingDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ReadingDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun orderedReadingsRetainIdentityDateAndExactDecimal() = runBlocking {
        val dao = database.readings()
        val early = Reading("first", LocalDate.parse("2026-01-02"), BigDecimal("12345678901234567890.0012300"))
        val later = Reading("second", LocalDate.parse("2026-02-03"), BigDecimal("12345678901234567891.000"))
        dao.insert(later.toEntity())
        dao.insert(early.toEntity())

        assertEquals(listOf(later, early), dao.getAll().map { it.toReading() })
    }

    @Test
    fun dateMustBeUnique() = runBlocking {
        val dao = database.readings()
        dao.insert(ReadingEntity("one", "2026-01-01", "1.0"))
        try {
            dao.insert(ReadingEntity("two", "2026-01-01", "2.0"))
            fail("Expected the unique date constraint to reject the second reading")
        } catch (_: SQLiteConstraintException) {
            assertEquals(listOf("one"), dao.getAll().map { it.id })
        }
    }
}
