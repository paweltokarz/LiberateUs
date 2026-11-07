package pl.local.liberateus.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface MessageContentDao {

    @Query(
        """
        SELECT *
        FROM message_contents
        WHERE
            accountId = :accountId
            AND messageId = :messageId
        LIMIT 1
        """
    )
    suspend fun get(
        accountId: String,
        messageId: String
    ): MessageContentEntity?

    @Upsert
    suspend fun upsert(
        content: MessageContentEntity
    )

    @Query(
        """
        DELETE FROM message_contents
        WHERE
            accountId = :accountId
            AND messageId = :messageId
        """
    )
    suspend fun delete(
        accountId: String,
        messageId: String
    )

    @Query(
        """
        DELETE FROM message_contents
        WHERE accountId = :accountId
        """
    )
    suspend fun deleteForAccount(
        accountId: String
    )
}
