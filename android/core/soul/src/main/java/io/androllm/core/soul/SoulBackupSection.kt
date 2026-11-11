package io.androllm.core.soul

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.androllm.core.backup.BackupSection
import io.androllm.core.common.MiniJson
import javax.inject.Inject
import javax.inject.Singleton

/**
 * soul.json: stanje duše (raspoloženje, energija, dosada, ljutnja, privrženost i vrijeme
 * zadnjeg kontakta) za "Izvezi sve". Tekst zadnje poruke se ne izvozi (to su razgovori).
 */
@Singleton
class SoulBackupSection @Inject constructor(
    private val engine: SoulEngine,
) : BackupSection {
    override val key: String = "soul"

    override suspend fun export(): String {
        val snap = engine.exportSnapshot()
        val s = snap.state
        return MiniJson.stringify(
            mapOf(
                "version" to 1,
                "mood" to s.mood,
                "energy" to s.energy,
                "boredom" to s.boredom,
                "anger" to s.anger,
                "attachment" to s.attachment,
                "lastContactMillis" to snap.lastContactMillis,
            ),
        )
    }

    override suspend fun import(json: String) {
        val m = MiniJson.parse(json) as? Map<*, *> ?: throw IllegalArgumentException("soul.json nije ispravan")
        val d = SoulState()
        fun num(name: String, default: Double): Double =
            ((m[name] as? Number)?.toDouble() ?: default).let { if (it.isFinite()) it.coerceIn(0.0, 100.0) else default }
        val state = SoulState(
            mood = num("mood", d.mood),
            energy = num("energy", d.energy),
            boredom = num("boredom", d.boredom),
            anger = num("anger", d.anger),
            attachment = num("attachment", d.attachment),
        )
        val last = (m["lastContactMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
        engine.restore(SoulSnapshot(state, last))
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SoulBackupModule {
    @Binds
    @IntoSet
    abstract fun bindSoulBackup(impl: SoulBackupSection): BackupSection
}
