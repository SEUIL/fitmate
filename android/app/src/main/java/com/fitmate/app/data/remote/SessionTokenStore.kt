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
internal data class RememberedAccount(val email: String, val refreshToken: String)
internal data class StoredSession(val tokens: SessionTokens? = null, val rememberedAccount: RememberedAccount? = null)

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

    @Synchronized fun read(): SessionTokens? = readState().tokens

    @Synchronized fun readState(): StoredSession {
        if (!file.baseFile.exists()) return StoredSession()
        val bytes = file.openRead().use { it.readBytes() }
        require(bytes.size > 12) { "저장된 로그인 정보를 읽지 못했습니다." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val json = String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        val objectValue = gson.fromJson(json, com.google.gson.JsonObject::class.java)
        // Preserve sessions saved by versions predating remembered accounts.
        val state = if (objectValue.has("accessToken")) StoredSession(gson.fromJson(objectValue, SessionTokens::class.java))
            else gson.fromJson(objectValue, StoredSession::class.java)
        state.tokens?.let { require(!it.accessToken.isNullOrBlank() && !it.refreshToken.isNullOrBlank()) }
        state.rememberedAccount?.let { require(!it.email.isNullOrBlank() && !it.refreshToken.isNullOrBlank()) }
        return state
    }

    @Synchronized fun write(tokens: SessionTokens?) = writeState(StoredSession(tokens))

    @Synchronized fun writeState(state: StoredSession) {
        if (state.tokens == null && state.rememberedAccount == null) { file.delete(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        // Active and remembered refresh tokens rotate in one atomic encrypted write.
        val bytes = cipher.iv + cipher.doFinal(gson.toJson(state).toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
}
