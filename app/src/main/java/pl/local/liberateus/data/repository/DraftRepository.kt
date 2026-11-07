package pl.local.liberateus.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import pl.local.liberateus.data.local.DraftDao
import pl.local.liberateus.data.mapper.toDomain
import pl.local.liberateus.data.mapper.toEntity
import pl.local.liberateus.data.remote.DraftsRemoteDataSource
import pl.local.liberateus.domain.model.Draft

class DraftRepository(
    private val remote:
        DraftsRemoteDataSource,
    private val dao:
        DraftDao,
    private val clock:
        () -> Long = {
            System.currentTimeMillis()
        }
) {

    fun observeDrafts(
        accountId: String
    ): Flow<List<Draft>> {
        requireAccountId(
            accountId
        )

        return dao
            .observeDrafts(
                accountId
            )
            .map {
                rows ->

                rows.map {
                    it.toDomain()
                }
            }
    }

    suspend fun getLocalDraft(
        accountId: String,
        draftId: String
    ): Draft? {
        requireAccountId(
            accountId
        )

        require(
            draftId.isNotBlank()
        ) {
            "draftId must not be blank"
        }

        return dao
            .getDraft(
                accountId,
                draftId
            )
            ?.toDomain()
    }

    suspend fun localDraftCount(
        accountId: String
    ): Int {
        requireAccountId(
            accountId
        )

        return dao.countDrafts(
            accountId
        )
    }

    suspend fun syncDrafts(
        accountId: String,
        limit: Int = 100
    ): DraftSyncResult {
        requireAccountId(
            accountId
        )

        require(
            limit > 0
        ) {
            "limit must be greater than zero"
        }

        val page =
            withContext(
                Dispatchers.IO
            ) {
                remote.getDrafts(
                    limit
                )
            }

        val now =
            clock()

        val entities =
            page.drafts
                .mapIndexed {
                    index,
                    draft ->

                    draft.toEntity(
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
            page.drafts.size >=
                page.total

        if (
            completeSnapshot
        ) {
            val ids =
                entities.map {
                    it.draftId
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
                    draftIds =
                        ids
                )
            }
        }

        return DraftSyncResult(
            fetchedCount =
                page.drafts.size,
            remoteTotal =
                page.total,
            completeSnapshot =
                completeSnapshot,
            syncedAt =
                now
        )
    }

    private fun requireAccountId(
        accountId: String
    ) {
        require(
            accountId.isNotBlank()
        ) {
            "accountId must not be blank"
        }
    }
}
