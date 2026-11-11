package io.androllm.core.extensions

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registar funkcija. Pri pravljenju dobija sve [Feature] objekte koje su moduli
 * prijavili Hilt skupom (`@Binds @IntoSet`, vidi [Feature]), a nove se mogu
 * dodavati i uklanjati u toku rada.
 *
 * Provjera dozvola nije ugrađena: [active] prima funkciju `(dozvola) -> odobreno`,
 * pa registar ne zavisi od sloja dozvola ni postavki.
 */
@Singleton
class ExtensionRegistry @Inject constructor(
    initial: @JvmSuppressWildcards Set<Feature>,
) {
    private val features = linkedMapOf<String, Feature>()

    /** Prazan registar (za testove i ručno sastavljanje). */
    constructor() : this(emptySet())

    init {
        // Sortirano po id-u da redoslijed ne zavisi od redoslijeda Hilt skupa.
        initial.sortedBy { it.id }.forEach { register(it) }
    }

    /** Dodaje funkciju. Dva puta isti id nije dozvoljen. */
    @Synchronized
    fun register(feature: Feature) {
        require(feature.id.isNotBlank()) { "Funkcija mora imati id" }
        require(feature.id !in features) { "Funkcija već postoji: ${feature.id}" }
        features[feature.id] = feature
    }

    /** Uklanja funkciju; nepoznat id se ignoriše. */
    @Synchronized
    fun unregister(id: String) {
        features.remove(id)
    }

    /** Sve registrovane funkcije, bez obzira na dozvole. */
    @Synchronized
    fun all(): List<Feature> = features.values.toList()

    @Synchronized
    fun get(id: String): Feature? = features[id]

    /** Funkcije čije su sve tražene dozvole odobrene prema [isGranted]. */
    fun active(isGranted: (String) -> Boolean): List<Feature> =
        all().filter { f -> f.requires.all(isGranted) }

    fun isActive(id: String, isGranted: (String) -> Boolean): Boolean =
        get(id)?.requires?.all(isGranted) ?: false
}
