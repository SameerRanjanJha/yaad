package com.sameer.yaad.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [NoteEntity::class, ItemEntity::class], version = 1, exportSchema = true)
abstract class YaadDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao
    abstract fun items(): ItemDao
}
