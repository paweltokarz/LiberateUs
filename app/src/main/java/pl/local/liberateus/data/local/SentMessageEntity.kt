package pl.local.liberateus.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName =
        "sent_messages",

    primaryKeys = [
        "accountId",
        "messageId"
    ],

    indices = [
        Index(
            value = [
                "accountId"
            ]
        )
    ]
)
data class SentMessageEntity(
    val accountId: String,
    val messageId: String,
    val receiver: String,
    val receiverAccountId: String?,
    val receiverUuid: String?,
    val topic: String,
    val body: String,
    val sendDateRaw: String,
    val hasAttachment: Boolean,
    val category: String?,
    val remoteOrder: Int,
    val lastSeenAt: Long
)
