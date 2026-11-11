package io.androllm.core.soul

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SoulModule {
    @Provides
    @Singleton
    fun provideSoulEngine(@ApplicationContext context: Context): SoulEngine =
        SoulEngine(PrefsSoulStore(context))
}
