package pl.local.liberateus.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.local.liberateus.data.local.SentMessageDao
import pl.local.liberateus.data.local.SentMessageEntity
import pl.local.liberateus.data.remote.SentMessagesRemoteBackend
import pl.local.liberateus.data.remote.SentMessagesRemoteDataSource
import pl.local.liberateus.data.remote.dto.RemoteSentMessage
import pl.local.liberateus.data.remote.dto.RemoteSentPage

class SentMessageRepositoryTest {

    @Test
    fun `complete outbox snapshot deletes stale rows`() =
        runBlocking {
            val dao =
                FakeSentDao(
                    listOf(
                        entity(
                            "old"
                        )
                    )
                )

            val repository =
                repository(
                    dao =
                        dao,

                    page =
                        RemoteSentPage(
                            total = 1,

                            messages =
                                listOf(
                                    remote(
                                        "new"
                                    )
                                )
                        )
                )

            val result =
                repository
                    .syncSentMessages(
                        "account"
                    )

            assertTrue(
                result.completeSnapshot
            )

            assertEquals(
                listOf(
                    "new"
                ),

                dao.rows.map {
                    it.messageId
                }
            )
        }

    @Test
    fun `truncated outbox snapshot preserves old rows`() =
        runBlocking {
            val dao =
                FakeSentDao(
                    listOf(
                        entity(
                            "old"
                        )
                    )
                )

            val repository =
                repository(
                    dao =
                        dao,

                    page =
                        RemoteSentPage(
                            total = 2,

                            messages =
                                listOf(
                                    remote(
                                        "new"
                                    )
                                )
                        )
                )

            val result =
                repository
                    .syncSentMessages(
                        "account"
                    )

            assertFalse(
                result.completeSnapshot
            )

            assertEquals(
                setOf(
                    "old",
                    "new"
                ),

                dao.rows
                    .map {
                        it.messageId
                    }
                    .toSet()
            )
        }

    @Test
    fun `empty complete outbox clears account rows`() =
        runBlocking {
            val dao =
                FakeSentDao(
                    listOf(
                        entity(
                            "old"
                        )
                    )
                )

            val repository =
                repository(
                    dao =
                        dao,

                    page =
                        RemoteSentPage(
                            total = 0,
                            messages =
                                emptyList()
                        )
                )

            val result =
                repository
                    .syncSentMessages(
                        "account"
                    )

            assertTrue(
                result.completeSnapshot
            )

            assertTrue(
                dao.rows.isEmpty()
            )
        }

    @Test
    fun `sent fields survive remote room domain mapping`() =
        runBlocking {
            val dao =
                FakeSentDao()

            val repository =
                repository(
                    dao =
                        dao,

                    page =
                        RemoteSentPage(
                            total = 1,

                            messages =
                                listOf(
                                    RemoteSentMessage(
                                        id =
                                            "123",

                                        receiver =
                                            "Jan Kowalski",

                                        receiverAccountId =
                                            "500",

                                        receiverUuid =
                                            "uuid",

                                        topic =
                                            "Temat",

                                        body =
                                            "Treść",

                                        sendDateRaw =
                                            "2026-10-29",

                                        hasAttachment =
                                            true,

                                        category =
                                            "normal"
                                    )
                                )
                        )
                )

            repository
                .syncSentMessages(
                    "account"
                )

            val item =
                repository
                    .getLocalSentMessage(
                        accountId =
                            "account",

                        messageId =
                            "123"
                    )

            assertEquals(
                "Jan Kowalski",
                item?.receiver
            )

            assertEquals(
                "Temat",
                item?.topic
            )

            assertEquals(
                "Treść",
                item?.body
            )

            assertEquals(
                "500",
                item?.receiverAccountId
            )

            assertTrue(
                item?.hasAttachment ==
                    true
            )
        }

    private fun repository(
        dao: FakeSentDao,
        page: RemoteSentPage
    ): SentMessageRepository {
        val backend =
            object :
                SentMessagesRemoteBackend {

                override fun getSentMessages(
                    limit: Int
                ): RemoteSentPage =
                    page
            }

        return SentMessageRepository(
            remote =
                SentMessagesRemoteDataSource(
                    backend
                ),

            dao =
                dao,

            clock = {
                123456L
            }
        )
    }

    private fun remote(
        id: String
    ): RemoteSentMessage =
        RemoteSentMessage(
            id =
                id,

            receiver =
                "Receiver",

            receiverAccountId =
                null,

            receiverUuid =
                null,

            topic =
                "",

            body =
                "",

            sendDateRaw =
                "",

            hasAttachment =
                false,

            category =
                null
        )

    private fun entity(
        id: String
    ): SentMessageEntity =
        SentMessageEntity(
            accountId =
                "account",

            messageId =
                id,

            receiver =
                "Receiver",

            receiverAccountId =
                null,

            receiverUuid =
                null,

            topic =
                "",

            body =
                "",

            sendDateRaw =
                "",

            hasAttachment =
                false,

            category =
                null,

            remoteOrder =
                0,

            lastSeenAt =
                0
        )

    private class FakeSentDao(
        initial:
            List<SentMessageEntity> =
            emptyList()
    ) : SentMessageDao {

        private val state =
            MutableStateFlow(
                initial
            )

        val rows:
            List<SentMessageEntity>
            get() =
                state.value

        override fun observeSentMessages(
            accountId: String
        ): Flow<List<SentMessageEntity>> =
            state

        override suspend fun getSentMessage(
            accountId: String,
            messageId: String
        ): SentMessageEntity? =
            state.value
                .firstOrNull {
                    it.accountId ==
                        accountId
                        &&
                        it.messageId ==
                            messageId
                }

        override suspend fun upsertAll(
            messages:
                List<SentMessageEntity>
        ) {
            val map =
                state.value
                    .associateBy {
                        it.accountId to
                            it.messageId
                    }
                    .toMutableMap()

            for (
                message in messages
            ) {
                map[
                    message.accountId to
                        message.messageId
                ] = message
            }

            state.value =
                map.values
                    .toList()
        }

        override suspend fun deleteMissing(
            accountId: String,
            messageIds: List<String>
        ) {
            state.value =
                state.value.filterNot {
                    it.accountId ==
                        accountId
                        &&
                        it.messageId !in
                            messageIds
                }
        }

        override suspend fun deleteAll(
            accountId: String
        ) {
            state.value =
                state.value.filterNot {
                    it.accountId ==
                        accountId
                }
        }

        override suspend fun countForAccount(
            accountId: String
        ): Int =
            state.value.count {
                it.accountId ==
                    accountId
            }
    }
}
