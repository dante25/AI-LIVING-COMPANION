package io.androllm.core.backup

/**
 * Jedan dio sigurnosne kopije "Izvezi sve". Svaki modul može imati svoj dio:
 * u zip-u se upisuje kao `<key>.json`.
 *
 * ## Kako modul dodaje svoj dio
 *
 * 1. Napiši klasu koja implementira [BackupSection] (modul mora imati
 *    `implementation(project(":core:backup"))`):
 *
 * ```kotlin
 * @Singleton
 * class SoulBackupSection @Inject constructor(private val engine: SoulEngine) : BackupSection {
 *     override val key = "soul"
 *     override suspend fun export(): String = ...   // JSON tekst
 *     override suspend fun import(json: String) { ... }
 * }
 * ```
 *
 * 2. Prijavi je u Hilt skup jednom linijom:
 *
 * ```kotlin
 * @Module @InstallIn(SingletonComponent::class)
 * abstract class SoulBackupModule {
 *     @Binds @IntoSet abstract fun bind(impl: SoulBackupSection): BackupSection
 * }
 * ```
 *
 * Pravila: dio NIKAD ne smije sadržavati ključeve, tokene ni druge tajne, a [import]
 * mora biti idempotentan (ponovljen uvoz ne smije umnožiti podatke).
 */
interface BackupSection {
    /** Mala slova, cifre, '_' ili '-'; postaje naziv fajla `<key>.json`. Mora biti jedinstven. */
    val key: String

    /** Vraća cijeli sadržaj dijela kao JSON tekst. */
    suspend fun export(): String

    /** Obnavlja podatke iz JSON teksta koji je ranije vratio [export]. Baca izuzetak pri grešci. */
    suspend fun import(json: String)
}
