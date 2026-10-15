package com.personal.wo

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personal.wo.core.*
import com.personal.wo.data.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DatabaseBackupTest {
    private lateinit var c:Context
    private lateinit var db:WoDatabase
    private lateinit var repo:WoRepository
    private val name="wo-instrumentation.db"
    @Before fun setUp()=runBlocking(Dispatchers.IO) {
        c=ApplicationProvider.getApplicationContext();c.deleteDatabase(name);System.loadLibrary("sqlcipher")
        val vault=KeyVault(c)
        db=Room.databaseBuilder(c,WoDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(vault.password())).build()
        repo=WoRepository(db,vault,EngineConfig(),c);repo.initialize();repo.command("start")
    }
    @After fun close() { db.close();c.deleteDatabase(name);c.cacheDir.listFiles()?.filter{it.name.startsWith("wo-test-") }?.forEach{it.delete()} }
    private fun sample():Observation { val now=System.currentTimeMillis();return Observation("notice-test",Source.NOTIFICATION,"notice-test",Kind.NOTICE,now,payload=Payload(label="测试元数据",category="通知片段")) }
    @Test fun databaseReallyIsEncrypted()=runBlocking(Dispatchers.IO) {
        val o=sample();repo.ingest(listOf(o),repo.cachedPolicy.generation)
        assertEquals(1,db.dao().observationCount(0,Long.MAX_VALUE))
        val header=ByteArray(16);c.getDatabasePath(name).inputStream().use{it.read(header)}
        assertFalse(header.contentEquals("SQLite format 3\u0000".toByteArray()))
    }
    @Test fun duplicatePauseAndDeletionArePersistent()=runBlocking(Dispatchers.IO) {
        val o=sample();val g=repo.cachedPolicy.generation;repo.ingest(listOf(o,o),g)
        assertEquals(1,db.dao().observationCount(0,Long.MAX_VALUE))
        repo.command("pause");repo.ingest(listOf(o.copy(id="stale",sourceKey="stale")),g)
        assertEquals(1,db.dao().observationCount(0,Long.MAX_VALUE))
        repo.command("resume");repo.delete(repo.preview(o.startMs,o.startMs+1));repo.ingest(listOf(o),repo.cachedPolicy.generation)
        assertEquals(0,db.dao().observationCount(0,Long.MAX_VALUE))
    }
    @Test fun backupAuthenticationAndRoundTrip()=runBlocking(Dispatchers.IO) {
        val o=sample();repo.ingest(listOf(o),repo.cachedPolicy.generation)
        val file=File(c.cacheDir,"wo-test-${UUID.randomUUID()}.woarchive");val uri=Uri.fromFile(file)
        val service=BackupService(c,repo);val recovery=service.export(uri)
        try { service.inspect(uri,"A".repeat(43));fail("wrong recovery key accepted") }catch(expected:IllegalArgumentException){}
        assertEquals(1,db.dao().observationCount(0,Long.MAX_VALUE))
        val inspection=service.inspect(uri,recovery);repo.command("stop");service.restore(inspection);repo.initialize()
        assertEquals(1,db.dao().observationCount(0,Long.MAX_VALUE));assertFalse(repo.cachedPolicy.requested)
        val bytes=file.readBytes();file.writeBytes(bytes.copyOf(bytes.size-10))
        try { service.inspect(uri,recovery);fail("truncated archive accepted") }catch(expected:IllegalArgumentException){}
        assertEquals(1,db.dao().observationCount(0,Long.MAX_VALUE))
    }
}
