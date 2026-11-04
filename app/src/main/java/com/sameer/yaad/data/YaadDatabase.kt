package com.sameer.yaad.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [NoteEntity::class, ItemEntity::class], version = 3, exportSchema = true)
abstract class YaadDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao
    abstract fun items(): ItemDao
}

/** Existing items start unchecked, so duplicates already on the list get removed too. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE items ADD COLUMN duplicateCheckedAt INTEGER")
        db.execSQL("ALTER TABLE items ADD COLUMN duplicateOfId INTEGER")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN reread INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE items ADD COLUMN replacedAt INTEGER")
    }
}
