package io.androllm.core.backup

/** Pomoćne funkcije za tolerantno čitanje vrijednosti iz MiniJson mapa. */
internal fun Map<*, *>.str(key: String): String? = this[key] as? String

internal fun Map<*, *>.long(key: String): Long? = (this[key] as? Number)?.toLong()

internal fun Map<*, *>.int(key: String): Int? = (this[key] as? Number)?.toInt()

internal fun Map<*, *>.double(key: String): Double? = (this[key] as? Number)?.toDouble()

internal fun Map<*, *>.bool(key: String): Boolean? = this[key] as? Boolean

internal fun Map<*, *>.list(key: String): List<*>? = this[key] as? List<*>

/**
 * Zaštita da se u sigurnosnu kopiju nikad ne upiše tajna: ime koje liči na ključ,
 * token, lozinku ili šifrovanu vrijednost se odbacuje i pri izvozu i pri uvozu.
 */
internal object SecretGuard {
    private val markers = listOf(
        "apikey", "api_key", "api-key", "secret", "token", "password", "passwd",
        "credential", "authorization", "bearer", "encrypted", "privatekey", "private_key",
    )

    fun isSecretName(name: String): Boolean {
        val n = name.lowercase()
        return markers.any { n.contains(it) } || n.endsWith("key")
    }

    fun <V> withoutSecrets(map: Map<String, V>): Map<String, V> =
        map.filterKeys { !isSecretName(it) }
}
