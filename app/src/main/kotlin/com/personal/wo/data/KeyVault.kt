package com.personal.wo.data

import android.content.Context
import com.personal.wo.core.lowerHex
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

class KeyVault(private val context: Context) {
    companion object { private val lock=Any();private val cache=mutableMapOf<String,ByteArray>() }
    private val alias = "wo.database.wrapper.v1"
    private val file = File(context.noBackupFilesDir, "vault-v1.bin")
    val deviceId:String=synchronized(lock) { val f=File(context.noBackupFilesDir,"source-device-id");if(f.exists())f.readText() else java.util.UUID.randomUUID().toString().also { f.writeText(it) } }
    private val store by lazy { KeyStore.getInstance("AndroidKeyStore").apply { load(null) } }
    @Synchronized private fun wrapper(): SecretKey {
        if (store.containsAlias(alias)) return store.getKey(alias, null) as SecretKey
        check(!file.exists()) { "D002:密钥不可用，原文件已保留；请从加密备份恢复" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setUserAuthenticationRequired(false).build())
        }.generateKey()
    }
    fun secrets(): ByteArray = synchronized(lock) {
        cache[file.absolutePath]?.let { return@synchronized it.copyOf() }
        val key = wrapper()
        if (file.exists()) {
            val data = file.readBytes(); check(data.size in 28..4096)
            return@synchronized Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data.copyOfRange(0,12)))
                updateAAD("wo-vault-v1".toByteArray()); doFinal(data.copyOfRange(12,data.size)).also { check(it.size == 64);cache[file.absolutePath]=it.copyOf() }
            }
        }
        check(!context.getDatabasePath("wo.db").exists()) { "D002:已有数据库但密钥缺失，禁止重建空库" }
        val data = ByteArray(64).also { SecureRandom().nextBytes(it) }; save(data)
        cache[file.absolutePath]=data.copyOf();data
    }
    private fun save(data: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrapper()); updateAAD("wo-vault-v1".toByteArray()) }
        val tmp = File(file.parentFile,"vault.tmp")
        tmp.outputStream().use { it.write(cipher.iv); it.write(cipher.doFinal(data)); it.fd.sync() }
        check(tmp.renameTo(file))
        synchronized(lock) { cache[file.absolutePath]=data.copyOf() }
    }
    fun restoreSeed(seed: ByteArray) { require(seed.size == 32); val s = secrets(); seed.copyInto(s,32); save(s); s.fill(0) }
    fun seed(): ByteArray = secrets().let { val b = it.copyOfRange(32,64); it.fill(0); b }
    fun password(): ByteArray = secrets().let { val s = Base64.encode(it.copyOfRange(0,32),Base64.NO_WRAP); it.fill(0); s }
    fun sourceKey(vararg fields: Any?): String {
        val secret = seed()
        return try { Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(secret,"HmacSHA256")); doFinal((deviceId+"\u0000"+fields.joinToString("\u0000")).toByteArray()).lowerHex() } } finally { secret.fill(0) }
    }
    fun writeProtected(name:String,plain:ByteArray)=synchronized(lock) {
        require(name in setOf("enhanced-config","shared-sync","deepseek-config"));require(plain.size<=16384)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,wrapper());updateAAD(name.toByteArray()) }
        val target=android.util.AtomicFile(File(context.noBackupFilesDir,"$name.bin"))
        val output=target.startWrite()
        try { output.write(cipher.iv+cipher.doFinal(plain));target.finishWrite(output) }
        catch(error:Throwable) { target.failWrite(output);throw error }
    }
    fun readProtected(name:String):ByteArray?=synchronized(lock) {
        require(name in setOf("enhanced-config","shared-sync","deepseek-config"));val f=File(context.noBackupFilesDir,"$name.bin")
        if(!f.exists()&&!File("${f.path}.bak").exists())return null
        val bytes=android.util.AtomicFile(f).openRead().use { it.readBytes() };require(bytes.size in 28..16412)
        Cipher.getInstance("AES/GCM/NoPadding").run { init(Cipher.DECRYPT_MODE,wrapper(),GCMParameterSpec(128,bytes.copyOfRange(0,12)));updateAAD(name.toByteArray());doFinal(bytes.copyOfRange(12,bytes.size)) }
    }
}
