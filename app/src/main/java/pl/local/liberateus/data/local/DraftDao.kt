package pl.local.liberateus.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftDao {

    @Query(
        """
        SELECT *
        FROM drafts
        WHERE
            accountId = :accountId
            AND deleted = 0
        ORDER BY
            remoteOrder ASC,
            lastSeenAt DESC
        """
    )
    fun observeDrafts(
        accountId: String
    ): Flow<List<DraftEntity>>

    @Query(
        """
        SELECT *
        FROM drafts
        WHERE
            accountId = :accountId
            AND draftId = :draftId
        LIMIT 1
        """
    )
    suspend fun getDraft(
        accountId: String,
        draftId: String
    ): DraftEntity?

    @Upsert
    suspend fun upsertAll(
        drafts: List<DraftEntity>
    )

    @Query(
        """
        DELETE FROM drafts
        WHERE
            accountId = :accountId
            AND draftId NOT IN (:draftIds)
        """
    )
    suspend fun deleteMissing(
        accountId: String,
        draftIds: List<String>
    )

    @Query(
        """
        DELETE FROM drafts
        WHERE accountId = :accountId
        """
    )
    suspend fun deleteAll(
        accountId: String
    )

    @Query(
        """
        SELECT COUNT(*)
        FROM drafts
        WHERE
            accountId = :accountId
            AND deleted = 0
        """
    )
    suspend fun countDrafts(
        accountId: String
    ): Int
}
