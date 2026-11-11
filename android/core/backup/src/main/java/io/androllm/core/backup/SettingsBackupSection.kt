package io.androllm.core.backup

import io.androllm.core.common.MiniJson
import io.androllm.core.datastore.PreferencesDataStore
import io.androllm.core.models.ChatFontSize
import io.androllm.core.models.ThemeMode
import io.androllm.core.models.UiDensity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * settings.json: opšte postavke iz [PreferencesDataStore]. Uvijek se izostavljaju tajne:
 * ni ključevi, ni tokeni, ni API ključevi (npr. geminiApiKeyEncrypted se nikad ne izvozi),
 * a [SecretGuard] dodatno odbacuje svako ime koje liči na tajnu i pri izvozu i pri uvozu.
 * Putanje specifične za uređaj (storagePath, modelPath) i stanje uvođenja se ne prenose.
 */
@Singleton
class SettingsBackupSection @Inject constructor(
    private val store: PreferencesDataStore,
) : BackupSection {
    override val key: String = "settings"

    override suspend fun export(): String {
        val p = store.userPreferences.first()
        val values = linkedMapOf<String, Any?>(
            "theme" to p.theme.name,
            "language" to p.language,
            "developerMode" to p.developerMode,
            "fontSize" to p.fontSize.name,
            "markdownEnabled" to p.markdownEnabled,
            "codeWrapping" to p.codeWrapping,
            "messageAnimations" to p.messageAnimations,
            "autoScroll" to p.autoScroll,
            "typingIndicator" to p.typingIndicator,
            "displayName" to p.displayName,
            "username" to p.username,
            "avatarIndex" to p.avatarIndex,
            "accentColor" to p.accentColor,
            "dynamicColor" to p.dynamicColor,
            "blurIntensity" to p.blurIntensity.toDouble(),
            "uiDensity" to p.uiDensity.name,
            "chatWallpaper" to p.chatWallpaper,
            "reduceMotion" to p.reduceMotion,
        )
        val favorites = store.favoritePromptIds.first().sorted()
        return MiniJson.stringify(
            mapOf(
                "version" to 1,
                "values" to SecretGuard.withoutSecrets(values),
                "favoritePromptIds" to favorites,
            ),
        )
    }

    override suspend fun import(json: String) {
        val root = MiniJson.parse(json) as? Map<*, *> ?: throw BackupException("settings.json nije ispravan")
        val raw = root["values"] as? Map<*, *> ?: return
        val v = linkedMapOf<String, Any?>()
        for (entry in raw.entries) {
            val name = entry.key as? String ?: continue
            if (!SecretGuard.isSecretName(name)) v[name] = entry.value
        }

        (v["theme"] as? String)?.let { name ->
            runCatching { ThemeMode.valueOf(name) }.getOrNull()?.let { store.setTheme(it) }
        }
        (v["language"] as? String)?.let { store.setLanguage(it) }
        (v["developerMode"] as? Boolean)?.let { store.setDeveloperMode(it) }
        (v["fontSize"] as? String)?.let { name ->
            runCatching { ChatFontSize.valueOf(name) }.getOrNull()?.let { store.setFontSize(it) }
        }
        (v["markdownEnabled"] as? Boolean)?.let { store.setMarkdownEnabled(it) }
        (v["codeWrapping"] as? Boolean)?.let { store.setCodeWrapping(it) }
        (v["messageAnimations"] as? Boolean)?.let { store.setMessageAnimations(it) }
        (v["autoScroll"] as? Boolean)?.let { store.setAutoScroll(it) }
        (v["typingIndicator"] as? Boolean)?.let { store.setTypingIndicator(it) }
        (v["displayName"] as? String)?.let { store.setDisplayName(it) }
        (v["username"] as? String)?.let { store.setUsername(it) }
        (v["avatarIndex"] as? Number)?.let { store.setAvatarIndex(it.toInt()) }
        (v["accentColor"] as? String)?.let { store.setAccentColor(it) }
        (v["dynamicColor"] as? Boolean)?.let { store.setDynamicColor(it) }
        (v["blurIntensity"] as? Number)?.let { store.setBlurIntensity(it.toFloat()) }
        (v["uiDensity"] as? String)?.let { name ->
            runCatching { UiDensity.valueOf(name) }.getOrNull()?.let { store.setUiDensity(it) }
        }
        (v["chatWallpaper"] as? String)?.let { store.setChatWallpaper(it) }
        (v["reduceMotion"] as? Boolean)?.let { store.setReduceMotion(it) }

        for (id in root.list("favoritePromptIds").orEmpty()) {
            (id as? String)?.takeIf { it.isNotBlank() }?.let { store.setPromptFavorite(it, true) }
        }
    }
}
