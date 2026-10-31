package com.paulcoding.audibook.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Android-only: desktop databases start at the current version. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN fingerprint TEXT NOT NULL DEFAULT ''")
    }
}
