package pl.local.liberateus.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.local.liberateus.data.local.DraftDao
import pl.local.liberateus.data.local.DraftEntity
import pl.local.liberateus.data.remote.DraftsRemoteBackend
import pl.local.liberateus.data.remote.DraftsRemoteDataSource
import pl.local.liberateus.data.remote.dto.RemoteDraft
import pl.local.liberateus.data.remote.dto.RemoteDraftPage

class DraftRepositoryTest {

    @Test
    fun `complete snapshot deletes stale drafts`() =
        runBlocking {
            val dao =
                FakeDraftDao(
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
                        RemoteDraftPage(
                            total = 1,
                            drafts =
                                listOf(
                                    remote(
                                        "new"
                                    )
                                )
                        )
                )

            val result =
                repository.syncDrafts(
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
                    it.draftId
                }
            )
        }

    @Test
    fun `truncated snapshot preserves stale rows`() =
        runBlocking {
            val dao =
                FakeDraftDao(
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
                        RemoteDraftPage(
                            total = 2,
                            drafts =
                                listOf(
                                    remote(
                                        "new"
                                    )
                                )
                        )
                )

            val result =
                repository.syncDrafts(
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
                        it.draftId
                    }
                    .toSet()
            )
        }

    @Test
    fun `empty complete snapshot clears account drafts`() =
        runBlocking {
            val dao =
                FakeDraftDao(
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
                        RemoteDraftPage(
                            total = 0,
                            drafts =
                                emptyList()
                        )
                )

            val result =
                repository.syncDrafts(
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
    fun `draft fields survive remote room domain mapping`() =
        runBlocking {
            val dao =
                FakeDraftDao()

            val repository =
                repository(
                    dao =
                        dao,
                    page =
                        RemoteDraftPage(
                            total = 1,
                            drafts =
                                listOf(
                                    RemoteDraft(
                                        id =
                                            "123",
                                        ownerId =
                                            "5",
                                        parentMessageId =
                                            null,
                                        dateAddedRaw =
                                            """{"date":"2026-10-29"}""",
                                        deleted =
                                            false,
                                        hasAttachment =
                                            true,
                                        topic =
                                            "Temat",
                                        body =
                                            "Treść robocza",
                                        receiversJson =
                                            "[]",
                                        type =
                                            "normal"
                                    )
                                )
                        )
                )

            repository.syncDrafts(
                "account"
            )

            val draft =
                repository.getLocalDraft(
                    accountId =
                        "account",
                    draftId =
                        "123"
                )

            assertEquals(
                "Temat",
                draft?.topic
            )

            assertEquals(
                "Treść robocza",
                draft?.body
            )

            assertEquals(
                "5",
                draft?.ownerId
            )

            assertTrue(
                draft?.hasAttachment ==
                    true
            )

            assertNull(
                draft?.parentMessageId
            )
        }

    private fun repository(
        dao: FakeDraftDao,
        page: RemoteDraftPage
    ): DraftRepository {
        val backend =
            object :
                DraftsRemoteBackend {

                override fun getDrafts(
                    limit: Int
                ): RemoteDraftPage =
                    page
            }

        return DraftRepository(
            remote =
                DraftsRemoteDataSource(
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
    ): RemoteDraft =
        RemoteDraft(
            id =
                id,
            ownerId =
                null,
            parentMessageId =
                null,
            dateAddedRaw =
                null,
            deleted =
                false,
            hasAttachment =
                false,
            topic =
                "",
            body =
                "",
            receiversJson =
                "[]",
            type =
                null
        )

    private fun entity(
        id: String
    ): DraftEntity =
        DraftEntity(
            accountId =
                "account",
            draftId =
                id,
            ownerId =
                null,
            parentMessageId =
                null,
            dateAddedRaw =
                null,
            deleted =
                false,
            hasAttachment =
                false,
            topic =
                "",
            body =
                "",
            receiversJson =
                "[]",
            type =
                null,
            remoteOrder =
                0,
            lastSeenAt =
                0
        )

    private class FakeDraftDao(
        initial:
            List<DraftEntity> =
            emptyList()
    ) : DraftDao {

        private val state =
            MutableStateFlow(
                initial
            )

        val rows:
            List<DraftEntity>
            get() =
                state.value

        override fun observeDrafts(
            accountId: String
        ): Flow<List<DraftEntity>> =
            state

        override suspend fun getDraft(
            accountId: String,
            draftId: String
        ): DraftEntity? =
            state.value
                .firstOrNull {
                    it.accountId ==
                        accountId &&
                        it.draftId ==
                        draftId
                }

        override suspend fun upsertAll(
            drafts:
                List<DraftEntity>
        ) {
            val rowsById =
                state.value
                    .associateBy {
                        it.accountId to
                            it.draftId
                    }
                    .toMutableMap()

            for (
                draft in drafts
            ) {
                rowsById[
                    draft.accountId to
                        draft.draftId
                ] = draft
            }

            state.value =
                rowsById
                    .values
                    .toList()
        }

        override suspend fun deleteMissing(
            accountId: String,
            draftIds: List<String>
        ) {
            state.value =
                state.value
                    .filterNot {
                        it.accountId ==
                            accountId &&
                            it.draftId !in
                            draftIds
                    }
        }

        override suspend fun deleteAll(
            accountId: String
        ) {
            state.value =
                state.value
                    .filterNot {
                        it.accountId ==
                            accountId
                    }
        }

        override suspend fun countDrafts(
            accountId: String
        ): Int =
            state.value
                .count {
                    it.accountId ==
                        accountId &&
                        !it.deleted
                }
    }
}
