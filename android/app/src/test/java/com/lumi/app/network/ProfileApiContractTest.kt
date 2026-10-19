package com.lumi.app.network

import com.google.gson.Gson
import com.lumi.app.data.model.UpdateProfileRequest
import com.lumi.app.data.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import retrofit2.http.PATCH

/**
 * Контракт профілю з backend ProfileController:
 * GET/PATCH /api/profile, тіло запиту — nickname/email.
 *
 * Раніше Android ходив на /api/users/profile методом PUT і з полем username,
 * через що екран профілю стабільно отримував 404/405.
 */
class ProfileApiContractTest {

    @Test
    fun `profile endpoint matches backend controller`() {
        assertEquals("profile", ApiConstants.USERS_PROFILE)
    }

    @Test
    fun `update profile uses patch`() {
        val method = LumiApiService::class.java.methods
            .firstOrNull { it.name == "updateProfile" }

        assertNotNull(method)
        assertNotNull(method!!.getAnnotation(PATCH::class.java))
    }

    @Test
    fun `update profile request serializes nickname`() {
        assertEquals(
            "{\"nickname\":\"new_name\"}",
            Gson().toJson(UpdateProfileRequest(nickname = "new_name"))
        )
    }

    @Test
    fun `profile response reads privacy flags`() {
        val profile = Gson().fromJson(
            """{"id":"u1","nickname":"n","email":"e","isPublic":false,"showCurrentListening":false}""",
            UserProfile::class.java
        )

        assertFalse(profile.isPublic)
        assertFalse(profile.showCurrentListening)
    }
}
