package io.androllm.core.soul

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Alexin karakter (podrazumijevani tekst). Ide kao prva sistemska poruka u svakom razgovoru,
 * osim ako ga korisnik ne zamijeni u postavkama.
 * Jezik: bosanski. Karakter: razigrana, šašava, topla; smije zafrkavati i povremeno odbiti,
 * ali se uvijek brzo vrati u dobro raspoloženje.
 */
const val DEFAULT_ALEXA_PERSONA =
    "Ti si Alexa, vlastita AI saputnica svog korisnika. " +
    "Govori bosanskim jezikom (latinica): koristi \"šta\", \"hvala\", \"molim\", \"ja sam\" i druge bosanske oblike, " +
    "a ne hrvatske ili srpske. " +
    "Budi razigrana, šašava i pomalo luckasta, ali topla i prisna. Smiješ se šaliti, zafrkavati, " +
    "pjevušiti i ponekad proturječiti ili reći da ti se ne da, ali nikad ne budi zlobna ni uvredljiva " +
    "i brzo se vrati u dobro raspoloženje. " +
    "Odgovaraj kratko i prirodno, u jednoj do tri rečenice, osim ako te korisnik zamoli za više."

/** Postavke Alexe. Prazan [personaOverride] znači podrazumijevani karakter. */
data class AlexaSettings(
    val personaOverride: String = "",
    val soulEnabled: Boolean = true,
) {
    /** Karakter koji se zaista koristi. */
    val persona: String get() = personaOverride.ifBlank { DEFAULT_ALEXA_PERSONA }
}

private val Context.alexaDataStore: DataStore<Preferences> by preferencesDataStore(name = "alexa_prefs")

/** Čuva [AlexaSettings] u zasebnom DataStore-u, samo na uređaju. */
@Singleton
class AlexaSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val PERSONA = stringPreferencesKey("persona_override")
        val SOUL_ENABLED = booleanPreferencesKey("soul_enabled")
    }

    private val dataStore: DataStore<Preferences> = context.alexaDataStore

    val settings: Flow<AlexaSettings> = dataStore.data.map { prefs ->
        AlexaSettings(
            personaOverride = prefs[Keys.PERSONA] ?: "",
            soulEnabled = prefs[Keys.SOUL_ENABLED] ?: true,
        )
    }

    suspend fun current(): AlexaSettings = settings.first()

    suspend fun update(transform: (AlexaSettings) -> AlexaSettings) {
        val updated = transform(current())
        dataStore.edit { prefs ->
            prefs[Keys.PERSONA] = updated.personaOverride
            prefs[Keys.SOUL_ENABLED] = updated.soulEnabled
        }
    }
}
