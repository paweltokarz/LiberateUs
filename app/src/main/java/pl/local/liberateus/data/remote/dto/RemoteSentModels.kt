package pl.local.liberateus.data.remote.dto

data class RemoteSentMessage(
    val id: String,
    val receiver: String,
    val receiverAccountId: String?,
    val receiverUuid: String?,
    val topic: String,
    val body: String,
    val sendDateRaw: String,
    val hasAttachment: Boolean,
    val category: String?
)

data class RemoteSentPage(
    val total: Int,
    val messages: List<RemoteSentMessage>
)
