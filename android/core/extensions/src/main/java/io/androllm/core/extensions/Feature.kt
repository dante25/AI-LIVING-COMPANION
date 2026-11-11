package io.androllm.core.extensions

/**
 * Jedna dodatna funkcija aplikacije (npr. pretraga weba, podsjetnici, nova vrsta alata).
 *
 * Interfejs je namjerno mali i ne zavisi ni od jednog drugog modula: dozvole se opisuju
 * običnim tekstovima ([requires]), a ko ih provjerava određuje pozivalac preko funkcije
 * koju prosljeđuje u [ExtensionRegistry.active].
 *
 * ## Kako se dodaje NOVA funkcija (bez izmjene jezgra)
 *
 * 1. U bilo kojem modulu koji vidi `core:extensions` napiši jednu klasu:
 *
 * ```kotlin
 * class WebSearchFeature @Inject constructor() : Feature {
 *     override val id = "web-search"
 *     override val displayName = "Pretraga weba"
 *     override val requires = setOf("internet")
 * }
 * ```
 *
 * 2. U (novom ili postojećem) Hilt modulu dodaj JEDNU liniju:
 *
 * ```kotlin
 * @Module
 * @InstallIn(SingletonComponent::class)
 * abstract class WebSearchModule {
 *     @Binds @IntoSet
 *     abstract fun bindWebSearch(impl: WebSearchFeature): Feature
 * }
 * ```
 *
 * To je sve. [ExtensionRegistry] dobija skup svih funkcija preko Hilta, pa nijedan
 * postojeći fajl ne treba mijenjati. Modul koji dodaje funkciju mora u build.gradle.kts
 * imati `implementation(project(":core:extensions"))`, a app modul mora zavisiti od tog modula.
 *
 * Funkcije se mogu i registrovati u toku rada: [ExtensionRegistry.register] i
 * [ExtensionRegistry.unregister].
 */
interface Feature {
    /** Stabilan, jedinstven identifikator (npr. "web-search"). */
    val id: String

    /** Naziv koji se prikazuje korisniku. */
    val displayName: String

    /**
     * Dozvole od kojih funkcija zavisi, kao tekstovi (npr. "internet", "kontakti").
     * Funkcija je aktivna samo ako su SVE dozvole odobrene. Prazan skup znači da
     * funkcija ne traži ništa.
     */
    val requires: Set<String> get() = emptySet()
}
