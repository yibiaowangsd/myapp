package com.personal.wo

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.personal.wo.data.DatabaseOpenCallback
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class DatabaseOpenCallbackTest {
    // Enforce the driver contract: a row-producing statement needs a consumed Cursor.
    // This test double does not pretend to execute Android's SQLCipher native library.
    private class RowReturningDatabase(val readFailure:RuntimeException?=null) {
        var consumed=false
        var closed=false
        var busyTimeout:Int?=null
        private val cursor=Proxy.newProxyInstance(Cursor::class.java.classLoader,arrayOf(Cursor::class.java)) { _,method,_ ->
            when(method.name) {
                "moveToFirst" -> { readFailure?.let { throw it };consumed=true;busyTimeout=5000;true }
                "getInt" -> 5000
                "close" -> { closed=true;null }
                "toString" -> "busy_timeout result cursor"
                else -> error("Unexpected cursor method: ${method.name}")
            }
        } as Cursor
        val database=Proxy.newProxyInstance(SupportSQLiteDatabase::class.java.classLoader,arrayOf(SupportSQLiteDatabase::class.java)) { _,method,args ->
            when(method.name) {
                "query" -> { assertEquals("PRAGMA busy_timeout=5000",args!![0]);cursor }
                "execSQL" -> error("A row-producing PRAGMA cannot use executeUpdateDelete/execSQL")
                "toString" -> "strict row-returning database"
                else -> error("Unexpected database method: ${method.name}")
            }
        } as SupportSQLiteDatabase
    }
    @Test fun openingConsumesThePragmaResultAndClosesTheCursor() {
        val driver=RowReturningDatabase()
        DatabaseOpenCallback().onOpen(driver.database)
        assertEquals(5000,driver.busyTimeout)
        assertTrue(driver.consumed)
        assertTrue(driver.closed)
    }
    @Test fun readingFailureStillClosesTheCursor() {
        val failure=IllegalStateException("driver failed reading result")
        val driver=RowReturningDatabase(failure)
        try { DatabaseOpenCallback().onOpen(driver.database);fail("Expected read failure") }
        catch(actual:IllegalStateException) { assertSame(failure,actual) }
        assertTrue(driver.closed)
    }
}
