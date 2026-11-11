package io.androllm.core.packs

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PacksModule {
    /** Trajno čuvanje paketa u filesDir/packs. */
    @Provides
    @Singleton
    fun provideContentStore(@ApplicationContext context: Context): ContentStore =
        ContentStore(File(context.filesDir, "packs"))
}
