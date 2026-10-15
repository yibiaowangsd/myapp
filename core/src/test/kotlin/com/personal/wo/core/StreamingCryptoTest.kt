package com.personal.wo.core

import com.google.crypto.tink.*
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import com.google.crypto.tink.subtle.AesGcmJce
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.security.SecureRandom

class StreamingCryptoTest {
    private fun primitive():StreamingAead { StreamingAeadConfig.register();return KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM_HKDF_1MB")).getPrimitive(StreamingAead::class.java) }
    @Test fun fullLargeStreamRoundTripAndAuthentication() {
        val p=primitive();val aad="WOARCH01-test-header".toByteArray();val data=ByteArray(2*1024*1024+117).also { SecureRandom().nextBytes(it) }
        val out=ByteArrayOutputStream();p.newEncryptingStream(out,aad).use { it.write(data) };val bytes=out.toByteArray()
        assertArrayEquals(data,p.newDecryptingStream(ByteArrayInputStream(bytes),aad).use { it.readBytes() })
        val mutations=listOf(bytes.copyOf(bytes.size-10),bytes.copyOf().also { it[it.size/2]=(it[it.size/2].toInt() xor 1).toByte() })
        mutations.forEach { bad->try { p.newDecryptingStream(ByteArrayInputStream(bad),aad).use { it.readBytes() };fail("unauthenticated stream accepted") }catch(expected:Exception){} }
        try { p.newDecryptingStream(ByteArrayInputStream(bytes),"modified header".toByteArray()).use { it.readBytes() };fail("modified associated header accepted") }catch(expected:Exception){}
    }
    @Test fun independentRecoveryKeyWrapsKeyset() {
        StreamingAeadConfig.register();val key=ByteArray(32).also { SecureRandom().nextBytes(it) };val handle=KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM_HKDF_1MB"))
        val wrapped=ByteArrayOutputStream().also { handle.write(JsonKeysetWriter.withOutputStream(it),AesGcmJce(key)) }.toByteArray()
        val restored=KeysetHandle.read(JsonKeysetReader.withBytes(wrapped),AesGcmJce(key)).getPrimitive(StreamingAead::class.java)
        val out=ByteArrayOutputStream();handle.getPrimitive(StreamingAead::class.java).newEncryptingStream(out,byteArrayOf(1)).use { it.write("archive".toByteArray()) }
        assertEquals("archive",restored.newDecryptingStream(ByteArrayInputStream(out.toByteArray()),byteArrayOf(1)).use { it.readBytes().decodeToString() })
        try { KeysetHandle.read(JsonKeysetReader.withBytes(wrapped),AesGcmJce(ByteArray(32)));fail("wrong key accepted") }catch(expected:Exception){}
    }
}
