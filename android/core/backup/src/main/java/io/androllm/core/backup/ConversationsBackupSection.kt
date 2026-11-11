package io.androllm.core.backup

import io.androllm.core.common.MiniJson
import io.androllm.core.common.getOrDefault
import io.androllm.core.common.getOrThrow
import io.androllm.core.database.repository.ConversationRepository
import io.androllm.core.database.repository.MessageRepository
import io.androllm.core.models.Conversation
import io.androllm.core.models.Message
import io.androllm.core.models.MessageOrigin
import io.androllm.core.models.MessageRole
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

internal data class ConversationBundle(val conversation: Conversation, val messages: List<Message>)

/** Čist (bez Androida) pretvarač razgovora u JSON i nazad. */
internal object ConversationsCodec {
    const val VERSION = 1

    fun encode(items: List<ConversationBundle>): String {
        val list = items.map { b ->
            val c = b.conversation
            mapOf(
                "id" to c.id,
                "title" to c.title,
                "createdAt" to c.createdAt,
                "updatedAt" to c.updatedAt,
                "isPinned" to c.isPinned,
                "isArchived" to c.isArchived,
                "messages" to b.messages.map { m ->
                    mapOf(
                        "id" to m.id,
                        "role" to m.role.name,
                        "content" to m.content,
                        "timestamp" to m.timestamp,
                        "modelId" to m.modelId,
                        "isBookmarked" to m.isBookmarked,
                        "origin" to m.origin.name,
                        "attachmentsJson" to m.attachmentsJson,
                    )
                },
            )
        }
        return MiniJson.stringify(mapOf("version" to VERSION, "conversations" to list))
    }

    fun decode(json: String): List<ConversationBundle> {
        val root = MiniJson.parse(json) as? Map<*, *> ?: throw BackupException("conversations.json nije ispravan")
        val version = root.int("version") ?: 1
        if (version > VERSION) throw BackupException("Razgovori u kopiji su novije verzije od podržane")
        val out = mutableListOf<ConversationBundle>()
        for (raw in root.list("conversations").orEmpty()) {
            val c = raw as? Map<*, *> ?: continue
            val id = c.str("id")?.takeIf { it.isNotBlank() } ?: continue
            val messages = c.list("messages").orEmpty().mapNotNull { rawMsg ->
                val m = rawMsg as? Map<*, *> ?: return@mapNotNull null
                val mid = m.str("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Message(
                    id = mid,
                    conversationId = id,
                    role = runCatching { MessageRole.valueOf(m.str("role") ?: "") }.getOrNull() ?: MessageRole.USER,
                    content = m.str("content") ?: "",
                    timestamp = m.long("timestamp") ?: 0L,
                    isPending = false,
                    modelId = m.str("modelId"),
                    isBookmarked = m.bool("isBookmarked") ?: false,
                    origin = runCatching { MessageOrigin.valueOf(m.str("origin") ?: "") }
                        .getOrNull() ?: MessageOrigin.TYPED,
                    attachmentsJson = m.str("attachmentsJson") ?: "",
                )
            }
            val created = c.long("createdAt") ?: 0L
            val last = messages.maxOfOrNull { it.timestamp }
            out += ConversationBundle(
                Conversation(
                    id = id,
                    title = c.str("title") ?: "",
                    createdAt = created,
                    updatedAt = c.long("updatedAt") ?: last ?: created,
                    lastMessagePreview = messages.lastOrNull()?.content?.take(120),
                    messageCount = messages.size,
                    isPinned = c.bool("isPinned") ?: false,
                    isArchived = c.bool("isArchived") ?: false,
                ),
                messages,
            )
        }
        return out
    }
}

/**
 * conversations.json: svi razgovori s porukama. Jedinstveni ID razgovora i poruke znači da
 * ponovljen uvoz ne umnožava poruke: postojeći razgovor se ne dira, a poruke se upisuju po ID-u
 * (isti ID zamjenjuje, ne dodaje).
 */
@Singleton
class ConversationsBackupSection @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
) : BackupSection {
    override val key: String = "conversations"

    override suspend fun export(): String {
        val conversations = conversationRepository.getAll().first().getOrThrow()
        val bundles = conversations.map { c ->
            ConversationBundle(c, messageRepository.observeByConversationId(c.id).first())
        }
        return ConversationsCodec.encode(bundles)
    }

    override suspend fun import(json: String) {
        for (bundle in ConversationsCodec.decode(json)) {
            val exists = conversationRepository.existsById(bundle.conversation.id).getOrDefault(false)
            // REPLACE nad postojećim razgovorom bi kaskadno obrisao njegove poruke, zato se ne dira.
            if (!exists) conversationRepository.upsert(bundle.conversation).getOrThrow()
            for (chunk in bundle.messages.chunked(500)) {
                messageRepository.upsertAll(chunk).getOrThrow()
            }
        }
    }
}
