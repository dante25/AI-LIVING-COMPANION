package io.androllm.core.soul

import android.content.Context

data class SoulSnapshot(val state: SoulState, val lastContactMillis: Long, val lastText: String = "")

/** Čuvanje stanja između pokretanja aplikacije. Zamjenjivo (npr. šifrovan backup kasnije). */
interface SoulStore {
    fun load(): SoulSnapshot?
    fun save(snapshot: SoulSnapshot)
}

class PrefsSoulStore(context: Context) : SoulStore {
    private val prefs = context.getSharedPreferences("alexa-soul", Context.MODE_PRIVATE)

    override fun load(): SoulSnapshot? {
        if (!prefs.contains("last")) return null
        return SoulSnapshot(
            state = SoulState(
                mood = prefs.getFloat("mood", 60f).toDouble(),
                energy = prefs.getFloat("energy", 80f).toDouble(),
                boredom = prefs.getFloat("boredom", 10f).toDouble(),
                anger = prefs.getFloat("anger", 0f).toDouble(),
                attachment = prefs.getFloat("attachment", 40f).toDouble(),
            ),
            lastContactMillis = prefs.getLong("last", 0L),
            lastText = prefs.getString("lastText", "") ?: "",
        )
    }

    override fun save(snapshot: SoulSnapshot) {
        prefs.edit()
            .putFloat("mood", snapshot.state.mood.toFloat())
            .putFloat("energy", snapshot.state.energy.toFloat())
            .putFloat("boredom", snapshot.state.boredom.toFloat())
            .putFloat("anger", snapshot.state.anger.toFloat())
            .putFloat("attachment", snapshot.state.attachment.toFloat())
            .putLong("last", snapshot.lastContactMillis)
            .putString("lastText", snapshot.lastText)
            .apply()
    }
}
