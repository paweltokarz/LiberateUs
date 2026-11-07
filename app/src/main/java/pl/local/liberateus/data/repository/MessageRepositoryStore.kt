package pl.local.liberateus.data.repository

import kotlinx.coroutines.flow.Flow
import pl.local.liberateus.data.local.LiberateDatabase
import pl.local.liberateus.data.local.MessageContentEntity
import pl.local.liberateus.data.local.MessageEntity
import pl.local.liberateus.data.local.SyncStateEntity

internal interface MessageRepositoryStore {

    fun observeInbox(
        accountId: String
    ): Flow<List<MessageEntity>>

    suspend fun getMessage(
        accountId: String,
        messageId: String
    ): MessageEntity?

    suspend fun upsertMessages(
        messages: List<MessageEntity>
    )

    suspend fun countMessages(
        accountId: String
    ): Int

    suspend fun markRead(
        accountId: String,
        messageId: String
    )

    suspend fun getContent(
        accountId: String,
        messageId: String
    ): MessageContentEntity?

    suspend fun upsertContent(
        content: MessageContentEntity
    )

    suspend fun getSyncState(
        accountId: String
    ): SyncStateEntity?

    suspend fun upsertSyncState(
        state: SyncStateEntity
    )
}

internal class RoomMessageRepositoryStore(
    private val database:
        LiberateDatabase
) : MessageRepositoryStore {

    override fun observeInbox(
        accountId: String
    ): Flow<List<MessageEntity>> =
        database
            .messageDao()
            .observeInbox(
                accountId
            )

    override suspend fun getMessage(
        accountId: String,
        messageId: String
    ): MessageEntity? =
        database
            .messageDao()
            .getById(
                accountId,
                messageId
            )

    override suspend fun upsertMessages(
        messages: List<MessageEntity>
    ) {
        if (messages.isEmpty()) {
            return
        }

        database
            .messageDao()
            .upsertAll(
                messages
            )
    }

    override suspend fun countMessages(
        accountId: String
    ): Int =
        database
            .messageDao()
            .countForAccount(
                accountId
            )

    override suspend fun markRead(
        accountId: String,
        messageId: String
    ) {
        database
            .messageDao()
            .markRead(
                accountId,
                messageId
            )
    }

    override suspend fun getContent(
        accountId: String,
        messageId: String
    ): MessageContentEntity? =
        database
            .messageContentDao()
            .get(
                accountId,
                messageId
            )

    override suspend fun upsertContent(
        content: MessageContentEntity
    ) {
        database
            .messageContentDao()
            .upsert(
                content
            )
    }

    override suspend fun getSyncState(
        accountId: String
    ): SyncStateEntity? =
        database
            .syncStateDao()
            .get(
                accountId
            )

    override suspend fun upsertSyncState(
        state: SyncStateEntity
    ) {
        database
            .syncStateDao()
            .upsert(
                state
            )
    }
}
