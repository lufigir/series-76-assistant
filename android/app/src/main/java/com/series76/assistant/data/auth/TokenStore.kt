package com.series76.assistant.data.auth

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.datastore.tink.AeadSerializer
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private object TextSerializer : Serializer<String> {
    override val defaultValue = ""

    override suspend fun readFrom(input: InputStream): String =
        try {
            input.readBytes().decodeToString(throwOnInvalidSequence = true)
        } catch (error: CharacterCodingException) {
            throw CorruptionException("The stored token is not text", error)
        }

    override suspend fun writeTo(t: String, output: OutputStream) = output.write(t.encodeToByteArray())
}

/** The token, encrypted at rest with a key held by the Android Keystore. */
class TokenStore(private val context: Context) {
    // Opening the Keystore is slow enough to drop frames, so it waits for the first read, on the IO dispatcher.
    private val store: DataStore<String> by lazy {
        DataStoreFactory.create(
            serializer = AeadSerializer(createAead(), TextSerializer, FILE_NAME.encodeToByteArray()),
            // A token that cannot be decrypted is the same as no token: the user signs in again.
            corruptionHandler = ReplaceFileCorruptionHandler { "" },
            produceFile = { context.dataStoreFile(FILE_NAME) },
        )
    }

    val token: Flow<String?> =
        flow { emitAll(store.data) }
            .map { it.ifEmpty { null } }
            .flowOn(Dispatchers.IO)

    suspend fun save(token: String) {
        withContext(Dispatchers.IO) { store.updateData { token } }
    }

    suspend fun clear() = save("")

    private fun createAead(): Aead {
        AeadConfig.register()
        return AndroidKeysetManager.Builder()
            .withSharedPref(context, "auth_keyset", "auth_keyset_preferences")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://series76_auth")
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private companion object {
        const val FILE_NAME = "auth_token.enc"
    }
}
