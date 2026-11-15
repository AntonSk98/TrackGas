package com.trackgas.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `daily_temperatures` (
                `date` TEXT NOT NULL,
                `location` TEXT NOT NULL,
                `celsius` TEXT NOT NULL,
                PRIMARY KEY(`date`, `location`)
            )""".trimIndent(),
        )
    }
}
