package pl.local.liberateus.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "drafts",
    primaryKeys = [
        "accountId",
        "draftId"
    ],
    indices = [
        Index(
            value = [
                "accountId"
            ]
        )
    ]
)
data class DraftEntity(
    val accountId: String,
    val draftId: String,
    val ownerId: String?,
    val parentMessageId: String?,
    val dateAddedRaw: String?,
    val deleted: Boolean,
    val hasAttachment: Boolean,
    val topic: String,
    val body: String,
    val receiversJson: String,
    val type: String?,
    val remoteOrder: Int,
    val lastSeenAt: Long
)
