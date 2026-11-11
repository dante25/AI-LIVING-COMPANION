package io.androllm.core.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds

/**
 * Skup svih [BackupSection]. Osnovni dijelovi (memorija, razgovori, postavke) su ovdje,
 * a ostali moduli (npr. core:soul) dodaju svoj jednom `@Binds @IntoSet` linijom.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {
    @Multibinds
    abstract fun sections(): Set<BackupSection>

    @Binds
    @IntoSet
    abstract fun bindMemory(impl: MemoryBackupSection): BackupSection

    @Binds
    @IntoSet
    abstract fun bindConversations(impl: ConversationsBackupSection): BackupSection

    @Binds
    @IntoSet
    abstract fun bindSettings(impl: SettingsBackupSection): BackupSection
}
