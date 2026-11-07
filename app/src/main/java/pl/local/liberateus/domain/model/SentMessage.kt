package pl.local.liberateus.domain.model

/**
 * One server-side message from Librus outbox.
 *
 * Read-only in Liberate Us 0.4.0.
 */
data class SentMessage(
    val id: String,
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
