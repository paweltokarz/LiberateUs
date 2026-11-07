package pl.local.liberateus.domain.model

data class Draft(
    val id: String,
    val ownerId: String?,
    val parentMessageId: String?,
    val dateAddedRaw: String?,
    val deleted: Boolean,
    val hasAttachment: Boolean,
    val topic: String,
    val body: String,
    val receivers: List<DraftRecipient>,
    val type: String?,
    val remoteOrder: Int,
    val lastSeenAt: Long,
    val receiversJson: String = "[]"
)

data class DraftRecipient(
    val id: String?,
    val name: String?
)
