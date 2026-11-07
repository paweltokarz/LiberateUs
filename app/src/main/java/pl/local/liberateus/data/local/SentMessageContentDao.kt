package pl.local.liberateus.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SentMessageContentDao {
    @Query(
        "SELECT * FROM sent_message_contents " +
            "WHERE accountId = :accountId AND messageId = :messageId LIMIT 1"
    )
    suspend fun get(
        accountId: String,
        messageId: String
    ): SentMessageContentEntity?

    @Upsert
    suspend fun upsert(value: SentMessageContentEntity)
}
