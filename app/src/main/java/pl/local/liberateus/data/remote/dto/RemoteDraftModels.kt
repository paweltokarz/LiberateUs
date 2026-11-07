package pl.local.liberateus.data.remote.dto

data class RemoteDraft(
    val id: String,
    val ownerId: String?,
    val parentMessageId: String?,
    val dateAddedRaw: String?,
    val deleted: Boolean,
    val hasAttachment: Boolean,
    val topic: String,
    val body: String,
    val receiversJson: String,
    val type: String?
)

data class RemoteDraftPage(
    val total: Int,
    val drafts: List<RemoteDraft>
)
