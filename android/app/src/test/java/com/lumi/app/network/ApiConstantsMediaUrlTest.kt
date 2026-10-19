package com.lumi.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Відносні шляхи медіа з БД ("/media/audio/...") перетворюються на абсолютні URL,
 * а готові абсолютні адреси не змінюються. Без цього ExoPlayer не знає, куди йти
 * за аудіо, а Coil — за обкладинкою.
 */
class ApiConstantsMediaUrlTest {

    /** Корінь бекенду без "/api": саме там віддається статика під /media/. */
    private val backendRoot =
        ApiConstants.BASE_URL.trimEnd('/').removeSuffix("/api").trimEnd('/')

    @Test
    fun `keeps absolute http url`() {
        assertEquals(
            "https://cdn.lumi.app/cover.png",
            ApiConstants.mediaUrl("https://cdn.lumi.app/cover.png")
        )
    }

    @Test
    fun `keeps content uri`() {
        assertEquals(
            "content://media/external/audio/42",
            ApiConstants.mediaUrl("content://media/external/audio/42")
        )
    }

    @Test
    fun `returns null for blank path`() {
        assertNull(ApiConstants.mediaUrl(null))
        assertNull(ApiConstants.mediaUrl("   "))
    }

    @Test
    fun `resolves relative media path against backend root`() {
        val url = ApiConstants.mediaUrl("/media/audio/demo/neon-overflow.wav")

        assertEquals("$backendRoot/media/audio/demo/neon-overflow.wav", url)
        assertFalse(url!!.contains("/api/media/"))
    }

    @Test
    fun `trims extra slashes`() {
        assertEquals(
            "$backendRoot/media/cover/demo/neon-nights.png",
            ApiConstants.mediaUrl("media/cover/demo/neon-nights.png")
        )
    }

    @Test
    fun `resolved url is absolute`() {
        assertTrue(
            ApiConstants.mediaUrl("/media/cover/demo/neon-nights.png")!!.startsWith("http")
        )
    }
}
