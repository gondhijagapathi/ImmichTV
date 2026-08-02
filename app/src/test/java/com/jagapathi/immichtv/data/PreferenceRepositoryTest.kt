package com.jagapathi.immichtv.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jagapathi.immichtv.model.ImmichCredentials
import com.jagapathi.immichtv.model.UserProfile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferenceRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: PreferenceRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = PreferenceRepository(context)
        repository.clearCredentials()
    }

    @Test
    fun `saveProfile should persist and update active profile`() {
        val profile = UserProfile(
            id = "test_user",
            name = "Test User",
            profilePictureUrl = "http://example.com/pic.jpg",
            credentials = ImmichCredentials("http://immich.local", "api_key_123")
        )

        repository.saveProfile(profile)

        val activeProfile = repository.activeProfile.value
        assertNotNull(activeProfile)
        assertEquals("test_user", activeProfile?.id)
        assertEquals("Test User", activeProfile?.name)
        assertEquals("http://example.com/pic.jpg", activeProfile?.profilePictureUrl)
        assertEquals("http://immich.local", activeProfile?.credentials?.serverUrl)
        assertEquals("api_key_123", activeProfile?.credentials?.apiKey)
    }

    @Test
    fun `saveMultipleProfiles and switch should work`() {
        val p1 = UserProfile("u1", "User 1", null, ImmichCredentials("url1", "key1"))
        val p2 = UserProfile("u2", "User 2", null, ImmichCredentials("url2", "key2"))

        repository.saveProfile(p1)
        repository.saveProfile(p2)

        assertEquals(2, repository.getAllProfiles().size)
        
        repository.setActiveProfile("u1")
        assertEquals("u1", repository.activeProfile.value?.id)
        
        repository.setActiveProfile("u2")
        assertEquals("u2", repository.activeProfile.value?.id)
    }

    @Test
    fun `deleteProfile should remove profile and update active if needed`() {
        val p1 = UserProfile("u1", "User 1", null, ImmichCredentials("url1", "key1"))
        repository.saveProfile(p1)
        
        assertEquals(1, repository.getAllProfiles().size)
        assertEquals("u1", repository.activeProfile.value?.id)
        
        repository.deleteProfile("u1")
        
        assertEquals(0, repository.getAllProfiles().size)
        assertNull(repository.activeProfile.value)
    }
}
