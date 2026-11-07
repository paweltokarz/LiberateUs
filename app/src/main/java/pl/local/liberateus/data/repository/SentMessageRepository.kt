package pl.local.liberateus.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import pl.local.liberateus.data.local.SentMessageDao
import pl.local.liberateus.data.local.SentMessageContentDao
import pl.local.liberateus.data.local.SentMessageContentEntity
import pl.local.liberateus.data.mapper.toDomain
import pl.local.liberateus.data.mapper.toEntity
import pl.local.liberateus.data.remote.SentMessagesRemoteDataSource
import pl.local.liberateus.domain.model.SentMessage

/**
 * Read-only repository for Librus outbox.
 */
class SentMessageRepository(
    private val remote:
        SentMessagesRemoteDataSource,

    private val dao:
        SentMessageDao,

    private val contentDao: SentMessageContentDao? = null,

    private val clock:
        () -> Long = {
            System.currentTimeMillis()
        }
) {

    fun observeSentMessages(
        accountId: String
    ): Flow<List<SentMessage>> {
        requireAccount(
            accountId
        )

        return dao
            .observeSentMessages(
                accountId
            )
            .map {
                rows ->

                rows.map {
                    it.toDomain()
                }
            }
    }

    suspend fun getLocalSentMessage(
        accountId: String,
        messageId: String
    ): SentMessage? {
        requireAccount(
            accountId
        )

        require(
            messageId.isNotBlank()
        )

        return dao
            .getSentMessage(
                accountId =
                    accountId,
                messageId =
                    messageId
            )
            ?.toDomain()
    }

    suspend fun getFullSentBody(
        accountId: String,
        messageId: String
    ): String {
        requireAccount(accountId)
        require(messageId.isNotBlank())
        val contents = checkNotNull(contentDao) {
            "Sent content cache unavailable"
        }
        contents.get(accountId, messageId)?.let {
            return it.content
        }
        val body = withContext(Dispatchers.IO) {
            remote.getFullSentBody(messageId)
        }
        // The row may have disappeared during a concurrent sync.
        if (dao.getSentMessage(accountId, messageId) != null) {
            contents.upsert(
                SentMessageContentEntity(
                    accountId = accountId,
                    messageId = messageId,
                    content = body,
                    fetchedAt = clock()
                )
            )
        }
        return body
    }

    suspend fun syncSentMessages(
        accountId: String,
        limit: Int = 100
    ): SentSyncResult {
        requireAccount(
            accountId
        )

        require(
            limit > 0
        )

        val page =
            withContext(
                Dispatchers.IO
            ) {
                remote
                    .getSentMessages(
                        limit
                    )
            }

        val now =
            clock()

        val entities =
            page.messages
                .mapIndexed {
                    index,
                    message ->

                    message.toEntity(
                        accountId =
                            accountId,

                        remoteOrder =
                            index,

                        observedAt =
                            now
                    )
                }

        dao.upsertAll(
            entities
        )

        val completeSnapshot =
            page.messages.size >=
                page.total

        if (
            completeSnapshot
        ) {
            val ids =
                entities.map {
                    it.messageId
                }

            if (
                ids.isEmpty()
            ) {
                dao.deleteAll(
                    accountId
                )
            } else {
                dao.deleteMissing(
                    accountId =
                        accountId,

                    messageIds =
                        ids
                )
            }
        }

        return SentSyncResult(
            fetchedCount =
                page.messages.size,

            remoteTotal =
                page.total,

            completeSnapshot =
                completeSnapshot,

            syncedAt =
                now
        )
    }

    suspend fun localCount(
        accountId: String
    ): Int {
        requireAccount(
            accountId
        )

        return dao
            .countForAccount(
                accountId
            )
    }

    private fun requireAccount(
        accountId: String
    ) {
        require(
            accountId.isNotBlank()
        ) {
            "accountId must not be blank"
        }
    }
}
