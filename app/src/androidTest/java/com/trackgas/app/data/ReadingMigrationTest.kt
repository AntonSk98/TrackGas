package com.trackgas.app.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackgas.app.tracking.*
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingMigrationTest {
    @Test fun v2ReplacementKeepsTheMeterWithTheLatestReadingCurrent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-v2-${UUID.randomUUID()}.db"
        val date = "2026-10-28"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
            legacy.execSQL("CREATE TABLE installations (id TEXT NOT NULL PRIMARY KEY, startDate TEXT)")
            legacy.execSQL("INSERT INTO installations VALUES ('old', NULL)")
            legacy.execSQL("INSERT INTO installations VALUES ('new', '$date')")
            legacy.execSQL("CREATE TABLE readings (id TEXT NOT NULL PRIMARY KEY, date TEXT NOT NULL, cubicMetres TEXT NOT NULL, installationId TEXT NOT NULL, FOREIGN KEY(installationId) REFERENCES installations(id) ON UPDATE RESTRICT ON DELETE RESTRICT)")
            legacy.execSQL("CREATE UNIQUE INDEX index_readings_installationId_date ON readings (installationId, date)")
            legacy.execSQL("INSERT INTO readings VALUES ('final', '$date', '10', 'old')")
            legacy.execSQL("INSERT INTO readings VALUES ('initial', '$date', '0', 'new')")
            legacy.execSQL("CREATE TABLE replacements (id TEXT NOT NULL PRIMARY KEY, date TEXT NOT NULL, oldInstallationId TEXT NOT NULL, newInstallationId TEXT NOT NULL, finalReadingId TEXT NOT NULL, initialReadingId TEXT NOT NULL, FOREIGN KEY(oldInstallationId) REFERENCES installations(id) ON UPDATE RESTRICT ON DELETE RESTRICT, FOREIGN KEY(newInstallationId) REFERENCES installations(id) ON UPDATE RESTRICT ON DELETE RESTRICT, FOREIGN KEY(finalReadingId) REFERENCES readings(id) ON UPDATE RESTRICT ON DELETE RESTRICT, FOREIGN KEY(initialReadingId) REFERENCES readings(id) ON UPDATE RESTRICT ON DELETE RESTRICT)")
            for (column in listOf("date", "oldInstallationId", "newInstallationId", "finalReadingId", "initialReadingId")) {
                legacy.execSQL("CREATE UNIQUE INDEX index_replacements_$column ON replacements ($column)")
            }
            legacy.execSQL("INSERT INTO replacements VALUES ('replacement', '$date', 'old', 'new', 'final', 'initial')")
            legacy.version = 2
        }

        val database = Room.databaseBuilder(context, ReadingDatabase::class.java, name)
            .build()
        try {
            val tracker = RoomReadingRepository(database).observeReadings().first().tracker
            assertEquals("new", tracker.currentInstallation.id)
            assertEquals(2, database.openHelper.readableDatabase.version)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun emptyAndPopulatedV1UpgradePreserveExactRowsAndColdReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (populated in listOf(false, true)) {
            val name = "migration-${UUID.randomUUID()}.db"
            val rows = if (populated) listOf(
                ReadingEntity("first-original-id", "2026-10-27", "1100.0000", ORIGINAL_INSTALLATION_ID),
                ReadingEntity("final-original-id", "2026-10-28", "1105.0012300", ORIGINAL_INSTALLATION_ID),
            ) else emptyList()
            val file = context.getDatabasePath(name)
            file.parentFile!!.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
                legacy.execSQL("CREATE TABLE readings (id TEXT NOT NULL PRIMARY KEY, date TEXT NOT NULL, cubicMetres TEXT NOT NULL)")
                legacy.execSQL("CREATE UNIQUE INDEX index_readings_date ON readings(date)")
                for (row in rows) legacy.execSQL(
                    "INSERT INTO readings VALUES (?, ?, ?)", arrayOf(row.id, row.date, row.cubicMetres),
                )
                legacy.version = 1
            }
            fun open() = Room.databaseBuilder(context, ReadingDatabase::class.java, name)
                .addMigrations(ReadingDatabase.MIGRATION_1_2)
                .addCallback(ReadingDatabase.INITIALIZE).build()
            var database = open()
            try {
                repeat(2) {
                    val tracker = RoomReadingRepository(database).observeReadings().first().tracker
                    assertEquals(rows.reversed(), database.readings().getAll())
                    assertEquals(listOf(InstallationEntity(ORIGINAL_INSTALLATION_ID)), database.readings().getInstallations())
                    assertEquals(ORIGINAL_INSTALLATION_ID, tracker.currentInstallation.id)
                    assertTrue(tracker.replacements.isEmpty())
                    assertEquals(if (populated) "5.0012300" else null,
                        readingIntervals(tracker).singleOrNull()?.cubicMetres?.toString())
                    assertEquals(2, database.openHelper.readableDatabase.version)
                    database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                    database.close()
                    database = open()
                }
                if (populated) {
                    try {
                        database.readings().insert(ReadingEntity("duplicate", rows.first().date, "1", ORIGINAL_INSTALLATION_ID))
                        fail("Expected migrated composite uniqueness")
                    } catch (_: SQLiteConstraintException) { }
                }
                try {
                    database.readings().insert(ReadingEntity("orphan", "2026-01-01", "1", "missing"))
                    fail("Expected migrated foreign key")
                } catch (_: SQLiteConstraintException) { }
                database.readings().insertInstallation(InstallationEntity("new", "2026-10-28"))
                database.readings().insert(ReadingEntity("baseline", "2026-10-28", "0.0000", "new"))
                assertEquals(rows.size + 1, database.readings().getAll().size)
            } finally {
                database.close()
                context.deleteDatabase(name)
            }
        }
    }
}
