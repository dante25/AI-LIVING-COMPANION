package io.androllm.core.extensions

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Deklariše skup [Feature] da Hilt radi i kad nijedna funkcija još nije prijavljena.
 * Funkcije dodaje svaki modul svojim `@Binds @IntoSet` (vidi [Feature]).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExtensionsModule {
    @Multibinds
    abstract fun featureSet(): Set<Feature>
}
