package pl.local.liberateus.data.remote.dto

/**
 * Payload for an ordinary Librus draft.
 *
 * The receivers object is preserved as JSON because
 * Librus supports multiple receiver categories.
 *
 * No message sending operation is exposed here.
 */
data class DraftWritePayload(
    val topic: String,
    val content: String,
    val receiversJson: String =
        """{"schoolReceivers":[]}""",
    val storageId: String? = null
)

data class DraftWriteResult(
    val identifier: String?,
    val httpCode: Int
)
