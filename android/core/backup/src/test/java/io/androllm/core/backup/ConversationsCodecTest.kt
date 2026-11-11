package io.androllm.core.backup

import io.androllm.core.models.Conversation
import io.androllm.core.models.Message
import io.androllm.core.models.MessageOrigin
import io.androllm.core.models.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConversationsCodecTest {
    private val conversation = Conversation(
        id = "c1", title = "Razgovor \"1\"", createdAt = 1_700_000_000_000L, updatedAt = 1_700_000_005_000L,
        isPinned = true,
    )
    private val messages = listOf(
        Message("m1", "c1", MessageRole.USER, "Zdravo\nsvijete š đ č ć ž", 1_700_000_001_000L),
        Message(
            "m2", "c1", MessageRole.ASSISTANT, "Ćao", 1_700_000_002_000L,
            isPending = true, modelId = "gemma", isBookmarked = true, origin = MessageOrigin.VOICE,
        ),
    )

    @Test
    fun roundTripKeepsIdsAndContent() {
        val decoded = ConversationsCodec.decode(
            ConversationsCodec.encode(listOf(ConversationBundle(conversation, messages))),
        ).single()
        assertEquals("c1", decoded.conversation.id)
        assertEquals("Razgovor \"1\"", decoded.conversation.title)
        assertEquals(true, decoded.conversation.isPinned)
        assertEquals(listOf("m1", "m2"), decoded.messages.map { it.id })
        assertEquals("Zdravo\nsvijete š đ č ć ž", decoded.messages[0].content)
        assertEquals(MessageOrigin.VOICE, decoded.messages[1].origin)
        assertEquals(1_700_000_002_000L, decoded.messages[1].timestamp)
        assertEquals("gemma", decoded.messages[1].modelId)
    }

    @Test
    fun pendingFlagIsNotRestored() {
        val decoded = ConversationsCodec.decode(
            ConversationsCodec.encode(listOf(ConversationBundle(conversation, messages))),
        ).single()
        assertFalse(decoded.messages[1].isPending)
    }

    @Test
    fun entriesWithoutIdAreSkipped() {
        val decoded = ConversationsCodec.decode(
            "{\"version\":1,\"conversations\":[{\"title\":\"bez id\"},{\"id\":\"c2\",\"messages\":[{\"content\":\"x\"}]}]}",
        )
        assertEquals(listOf("c2"), decoded.map { it.conversation.id })
        assertEquals(0, decoded.single().messages.size)
    }
}
