package alexa.features

import alexa.permissions.Capability
import alexa.permissions.Settings

/**
 * Funkcija koja se može dodati bez mijenjanja postojećeg koda.
 *
 * Nova funkcija se registruje u registru. Ona deklariše od kojih sposobnosti zavisi
 * (dozvole iz permissions sloja). Ako neka sposobnost nije uključena u postavkama,
 * funkcija se ne aktivira. Funkcije su podaci i kod iz core modula, a ne skripte iz paketa.
 */
interface Feature {
    val id: String
    val requires: Set<Capability>
}

class FeatureRegistry(private val settings: Settings) {
    private val features = linkedMapOf<String, Feature>()

    fun register(feature: Feature) {
        require(feature.id !in features) { "Funkcija već postoji: ${feature.id}" }
        features[feature.id] = feature
    }

    fun unregister(id: String) {
        features.remove(id)
    }

    /** Funkcije čije su sve zavisnosti uključene u postavkama. */
    fun active(): List<Feature> = features.values.filter { f ->
        f.requires.all { settings.isOn(it) }
    }

    fun isActive(id: String): Boolean = active().any { it.id == id }
}
