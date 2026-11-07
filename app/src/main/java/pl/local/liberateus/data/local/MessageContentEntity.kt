package pl.local.liberateus.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "message_contents",
    primaryKeys = [
        "accountId",
        "messageId"
    ],
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = [
                "accountId",
                "messageId"
            ],
            childColumns = [
                "accountId",
                "messageId"
            ],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(
            value = [
                "accountId",
                "messageId"
            ]
        )
    ]
)
data class MessageContentEntity(
    val accountId: String,
    val messageId: String,

    /*
     * Pełna treść trafia tutaj dopiero po świadomym
     * otwarciu wiadomości przez użytkownika.
     */
    val content: String,

    val fetchedAt: Long
)
