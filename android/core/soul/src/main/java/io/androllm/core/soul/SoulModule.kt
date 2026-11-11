package io.androllm.core.soul

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Module
@InstallIn(SingletonComponent::class)
object SoulModule {
    @Provides
    @Singleton
    fun provideSoulEngine(
        @ApplicationContext context: Context,
        alexaSettingsStore: AlexaSettingsStore,
    ): SoulEngine {
        val engine = SoulEngine(PrefsSoulStore(context))
        // Postavke se učitaju asinhrono; do tada vrijede podrazumijevane (duša uključena).
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            alexaSettingsStore.settings.collect { engine.applySettings(it) }
        }
        return engine
    }
}
