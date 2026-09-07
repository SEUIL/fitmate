package com.fitmate.app.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.google.gson.Gson
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class SessionTokens(val accessToken: String, val refreshToken: String)

/** Tokens never enter the profile snapshot or Android backup. */
internal class SessionTokenStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "api-session.bin"))
    private val alias = "fitmate.api.session"
    private val gson = Gson()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    @Synchronized fun read(): SessionTokens? {
        if (!file.baseFile.exists()) return null
        val bytes = file.openRead().use { it.readBytes() }
        require(bytes.size > 12) { "저장된 로그인 정보를 읽지 못했습니다." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return gson.fromJson(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8), SessionTokens::class.java)
    }

    @Synchronized fun write(tokens: SessionTokens?) {
        if (tokens == null) { file.delete(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(gson.toJson(tokens).toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
}
