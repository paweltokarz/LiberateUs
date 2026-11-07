package pl.local.liberateus.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SentMessageDao {

    @Query(
        """
        SELECT *
        FROM sent_messages
        WHERE accountId = :accountId
        ORDER BY
            remoteOrder ASC,
            lastSeenAt DESC
        """
    )
    fun observeSentMessages(
        accountId: String
    ): Flow<List<SentMessageEntity>>

    @Query(
        """
        SELECT *
        FROM sent_messages
        WHERE
            accountId = :accountId
            AND messageId = :messageId
        LIMIT 1
        """
    )
    suspend fun getSentMessage(
        accountId: String,
        messageId: String
    ): SentMessageEntity?

    @Upsert
    suspend fun upsertAll(
        messages:
            List<SentMessageEntity>
    )

    @Query(
        """
        DELETE FROM sent_messages
        WHERE
            accountId = :accountId
            AND messageId NOT IN (:messageIds)
        """
    )
    suspend fun deleteMissing(
        accountId: String,
        messageIds: List<String>
    )

    @Query(
        """
        DELETE FROM sent_messages
        WHERE accountId = :accountId
        """
    )
    suspend fun deleteAll(
        accountId: String
    )

    @Query(
        """
        SELECT COUNT(*)
        FROM sent_messages
        WHERE accountId = :accountId
        """
    )
    suspend fun countForAccount(
        accountId: String
    ): Int
}
