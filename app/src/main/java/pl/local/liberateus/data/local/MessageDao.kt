package pl.local.liberateus.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    /*
     * Docelowe źródło inboxa dla UI.
     *
     * lastSeenAt:
     *   grupuje wiadomości według ostatniej odpowiedzi serwera.
     *
     * remoteOrder:
     *   zachowuje kolejność zwróconą przez API.
     */
    @Query(
        """
        SELECT *
        FROM messages
        WHERE accountId = :accountId
        ORDER BY
            lastSeenAt DESC,
            remoteOrder ASC,
            firstSeenAt DESC
        """
    )
    fun observeInbox(
        accountId: String
    ): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT *
        FROM messages
        WHERE
            accountId = :accountId
            AND messageId = :messageId
        LIMIT 1
        """
    )
    suspend fun getById(
        accountId: String,
        messageId: String
    ): MessageEntity?

    @Query(
        """
        SELECT *
        FROM messages
        WHERE accountId = :accountId
        ORDER BY
            lastSeenAt DESC,
            remoteOrder ASC,
            firstSeenAt DESC
        """
    )
    suspend fun getInboxSnapshot(
        accountId: String
    ): List<MessageEntity>

    @Upsert
    suspend fun upsert(
        message: MessageEntity
    )

    @Upsert
    suspend fun upsertAll(
        messages: List<MessageEntity>
    )

    @Query(
        """
        UPDATE messages
        SET unread = 0
        WHERE
            accountId = :accountId
            AND messageId = :messageId
        """
    )
    suspend fun markRead(
        accountId: String,
        messageId: String
    )

    @Query(
        """
        SELECT COUNT(*)
        FROM messages
        WHERE accountId = :accountId
        """
    )
    suspend fun countForAccount(
        accountId: String
    ): Int

    @Query(
        """
        DELETE FROM messages
        WHERE
            accountId = :accountId
            AND messageId = :messageId
        """
    )
    suspend fun deleteById(
        accountId: String,
        messageId: String
    )

    @Query(
        """
        DELETE FROM messages
        WHERE accountId = :accountId
        """
    )
    suspend fun deleteForAccount(
        accountId: String
    )
}
