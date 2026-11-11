package io.androllm.core.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.androllm.core.common.getOrThrow
import io.androllm.core.memory.MemoryManager
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * memory.json: koristi postojeći [MemoryManager.exportMemories] i [MemoryManager.importMemories].
 * Uvoz u memoriji već sam spaja duplikate (po normalizovanom sadržaju unutar kategorije).
 */
@Singleton
class MemoryBackupSection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryManager: MemoryManager,
) : BackupSection {
    override val key: String = "memory"

    override suspend fun export(): String {
        val file = memoryManager.exportMemories().getOrThrow()
        return try {
            file.readText(Charsets.UTF_8)
        } finally {
            file.delete()
        }
    }

    override suspend fun import(json: String) {
        val dir = File(context.cacheDir, "backup_import").apply { mkdirs() }
        val file = File(dir, "memory_${System.nanoTime()}.json")
        try {
            file.writeText(json, Charsets.UTF_8)
            memoryManager.importMemories(file).getOrThrow()
        } finally {
            file.delete()
        }
    }
}
