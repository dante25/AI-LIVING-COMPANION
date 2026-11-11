package io.androllm.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretGuardTest {
    @Test
    fun secretLookingNamesAreDetected() {
        listOf(
            "geminiApiKeyEncrypted", "apiKey", "API_KEY", "accessToken", "mcpBearer",
            "password", "clientSecret", "authorization", "openaiKey",
        ).forEach { assertTrue(it, SecretGuard.isSecretName(it)) }
    }

    @Test
    fun ordinarySettingsAreKept() {
        listOf(
            "theme", "language", "displayName", "avatarIndex", "accentColor", "autoScroll",
            "typingIndicator", "chatWallpaper", "blurIntensity",
        ).forEach { assertFalse(it, SecretGuard.isSecretName(it)) }
    }

    @Test
    fun withoutSecretsFiltersMap() {
        val filtered = SecretGuard.withoutSecrets(mapOf("theme" to "DARK", "geminiApiKeyEncrypted" to "xyz"))
        assertEquals(mapOf("theme" to "DARK"), filtered)
    }
}
