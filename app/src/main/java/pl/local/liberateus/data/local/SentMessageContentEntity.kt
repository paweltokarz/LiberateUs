package pl.local.liberateus.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "sent_message_contents",
    primaryKeys = ["accountId", "messageId"],
    indices = [Index(value = ["accountId", "messageId"])],
    foreignKeys = [
        ForeignKey(
            entity = SentMessageEntity::class,
            parentColumns = ["accountId", "messageId"],
            childColumns = ["accountId", "messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SentMessageContentEntity(
    val accountId: String,
    val messageId: String,
    val content: String,
    val fetchedAt: Long
)
