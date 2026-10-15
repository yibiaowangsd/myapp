package com.personal.wo.data

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

internal class DatabaseOpenCallback : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        // Even the assignment returns a row; execSQL's update/delete path rejects it.
        // Consuming the lazy cursor applies the pragma, and use also closes on failure.
        db.query("PRAGMA busy_timeout=5000").use { cursor -> cursor.moveToFirst() }
    }
}
