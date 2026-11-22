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

internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_readings_date` ON `readings` (`date`)")
    }
}

internal val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_interval_temperatures_location_laterId` " +
                "ON `interval_temperatures` (`location`, `laterId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_daily_temperatures_location_date` " +
                "ON `daily_temperatures` (`location`, `date`)",
        )
    }
}
