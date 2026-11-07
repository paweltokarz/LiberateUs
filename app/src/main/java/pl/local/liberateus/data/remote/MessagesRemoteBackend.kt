package pl.local.liberateus.data.remote

import pl.local.liberateus.data.MessageFetchSource
import pl.local.liberateus.data.remote.dto.RemoteFullMessage
import pl.local.liberateus.data.remote.dto.RemoteInboxMessage

/**
 * Testable transport boundary used by MessagesRemoteDataSource.
 */
internal interface MessagesRemoteBackend {

    fun getInbox(
        limit: Int,
        source: MessageFetchSource
    ): List<RemoteInboxMessage>

    fun getMessage(
        messageId: String
    ): RemoteFullMessage
}

/**
 * Temporary adapter around the proven legacy HTTP implementation.
 *
 * It uses the same LibrusConnection as SessionManager,
 * therefore authentication and message requests share
 * one CookieJar and one authenticated session.
 */
internal class LibrusMessagesBackend(
    connection: LibrusConnection
) : MessagesRemoteBackend {

    private val client =
        connection.legacyClient

    override fun getInbox(
        limit: Int,
        source: MessageFetchSource
    ): List<RemoteInboxMessage> {

        /*
         * IMPORTANT:
         * only fetchInbox() is allowed here.
         *
         * No fetchMessage() calls belong in inbox synchronization.
         */
        return client
            .fetchInbox(
                limit,
                source
            )
            .map { message ->

                RemoteInboxMessage(
                    id = message.id,
                    sender = message.sender,
                    topic = message.topic,
                    sendDate =
                        message.sendDate,
                    unread = message.unread
                )
            }
    }

    override fun getMessage(
        messageId: String
    ): RemoteFullMessage {

        /*
         * This is the deliberate full-message path.
         * It may change server-side read state.
         */
        val message =
            client.fetchMessage(
                messageId
            )

        return RemoteFullMessage(
            id = message.id,
            sender = message.sender,
            topic = message.topic,
            sendDate =
                message.sendDate,
            content = message.content
        )
    }
}
