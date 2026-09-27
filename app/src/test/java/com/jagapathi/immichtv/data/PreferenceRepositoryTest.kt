package com.jagapathi.immichtv.data

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.jagapathi.immichtv.model.ImmichCredentials
import com.jagapathi.immichtv.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class PreferenceRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val key = newKey()
    private lateinit var scope: CoroutineScope
    private lateinit var repository: PreferenceRepository

    private val settingsFile get() = File(tempFolder.root, "app_settings")

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    /** Opens the settings file the same way the app does, but with a software key. */
    private fun open(key: SecretKey = this.key): PreferenceRepository {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = DataStoreFactory.create(
            serializer = AppSettingsSerializer(AesGcmCipher { key }),
            corruptionHandler = ReplaceFileCorruptionHandler { AppSettings() },
            scope = scope,
            produceFile = { settingsFile }
        )
        return PreferenceRepository(dataStore, scope)
    }

    /** Simulates an app restart: closes the DataStore and reads the file again. */
    private suspend fun restart(key: SecretKey = this.key): PreferenceRepository {
        scope.coroutineContext.job.cancelAndJoin()
        return open(key)
    }

    private suspend fun <T> StateFlow<T>.await(predicate: (T) -> Boolean): T =
        withTimeout(5_000) { first(predicate) }

    @Before
    fun setUp() {
        repository = open()
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `saveProfile persists encrypted and survives a restart`() = runBlocking {
        val profile = UserProfile(
            id = "test_user",
            name = "Test User",
            profilePictureUrl = "http://example.com/pic.jpg",
            credentials = ImmichCredentials("http://immich.local", "api_key_123")
        )

        repository.saveProfile(profile)
        assertEquals(profile, repository.activeProfile.await { it != null })

        val restarted = restart()
        assertEquals(profile, restarted.activeProfile.await { it != null })
        assertEquals("http://immich.local", restarted.baseUrl)
        assertEquals("api_key_123", restarted.apiKey)

        val onDisk = settingsFile.readBytes().decodeToString()
        assertFalse("API key must not be stored in plain text", onDisk.contains("api_key_123"))
        assertFalse(onDisk.contains("Test User"))
    }

    @Test
    fun `saveMultipleProfiles and switch should work`() = runBlocking {
        val p1 = UserProfile("u1", "User 1", null, ImmichCredentials("url1", "key1"))
        val p2 = UserProfile("u2", "User 2", null, ImmichCredentials("url2", "key2"))

        repository.saveProfile(p1)
        repository.saveProfile(p2)

        assertEquals(2, repository.getAllProfiles().size)
        assertEquals("u1", repository.activeProfile.await { it != null }?.id)

        repository.setActiveProfile("u2")
        assertEquals("u2", repository.activeProfile.await { it?.id == "u2" }?.id)
        assertEquals("key2", repository.apiKey)
    }

    @Test
    fun `deleteProfile should remove profile and update active if needed`() = runBlocking {
        val p1 = UserProfile("u1", "User 1", null, ImmichCredentials("url1", "key1"))
        repository.saveProfile(p1)
        repository.activeProfile.await { it != null }

        repository.deleteProfile("u1")

        assertEquals(0, repository.getAllProfiles().size)
        assertNull(repository.activeProfile.await { it == null })
        assertNull(repository.apiKey)
    }

    @Test
    fun `theme defaults to System and persists`() = runBlocking {
        repository.settings.await { it != null }
        assertEquals(AppTheme.System, repository.theme.value)

        repository.setTheme(AppTheme.Dark)

        assertEquals(AppTheme.Dark, restart().theme.await { it == AppTheme.Dark })
    }

    @Test
    fun `settings that can't be decrypted are reset instead of crashing`() = runBlocking {
        repository.saveProfile(UserProfile("u1", "User 1", null, ImmichCredentials("url1", "key1")))
        repository.setTheme(AppTheme.Light)

        // Same file, different key: what happens if the Keystore key is lost.
        val restarted = restart(newKey())
        val settings = restarted.settings.await { it != null }

        assertEquals(AppSettings(), settings)
        assertNull(restarted.activeProfile.value)
    }
}
