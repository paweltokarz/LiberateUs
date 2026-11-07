package pl.local.liberateus.data.remote

import pl.local.liberateus.data.MessageFetchSource
import pl.local.liberateus.data.remote.dto.RemoteFullMessage
import pl.local.liberateus.data.remote.dto.RemoteInboxMessage

/**
 * Public remote boundary for message operations.
 *
 * getInbox():
 * - metadata only,
 * - does not download message bodies,
 * - safe for synchronization.
 *
 * getMessage():
 * - downloads the full message,
 * - represents an explicit opening of a message,
 * - must not be called by background inbox synchronization.
 */
class MessagesRemoteDataSource internal constructor(
    private val backend:
        MessagesRemoteBackend
) {

    internal constructor(
        connection: LibrusConnection
    ) : this(
        LibrusMessagesBackend(
            connection
        )
    )

    fun getInbox(
        limit: Int = 10,
        source:
            MessageFetchSource =
            MessageFetchSource.UNKNOWN
    ): List<RemoteInboxMessage> {

        require(
            limit > 0
        ) {
            "limit must be greater than zero"
        }

        return backend.getInbox(
            limit = limit,
            source = source
        )
    }

    fun getMessage(
        messageId: String
    ): RemoteFullMessage {

        require(
            messageId.isNotBlank()
        ) {
            "messageId must not be blank"
        }

        return backend.getMessage(
            messageId
        )
    }
}
