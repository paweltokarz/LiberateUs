package pl.local.liberateus.data.remote

import pl.local.liberateus.data.remote.dto.RemoteSentMessage
import pl.local.liberateus.data.remote.dto.RemoteSentPage

internal interface SentMessagesRemoteBackend {

    fun getSentMessages(
        limit: Int
    ): RemoteSentPage

    fun getFullSentBody(messageId: String): String =
        throw UnsupportedOperationException("Sent detail unavailable")
}

class SentMessagesRemoteDataSource internal constructor(
    private val backend:
        SentMessagesRemoteBackend
) {

    internal constructor(
        connection: LibrusConnection
    ) : this(
        LibrusSentMessagesBackend(
            connection
        )
    )

    fun getFullSentBody(messageId: String): String {
        require(messageId.isNotBlank())
        return backend.getFullSentBody(messageId)
    }

    fun getSentMessages(
        limit: Int = 100
    ): RemoteSentPage {
        require(
            limit > 0
        ) {
            "limit must be greater than zero"
        }

        return backend
            .getSentMessages(
                limit
            )
    }
}

internal class LibrusSentMessagesBackend(
    connection: LibrusConnection
) : SentMessagesRemoteBackend {

    private val client =
        connection.legacyClient

    override fun getFullSentBody(messageId: String): String =
        client.fetchMessage(messageId, mailbox = "outbox").content

    override fun getSentMessages(
        limit: Int
    ): RemoteSentPage {
        val page =
            client.fetchSentMessages(
                limit
            )

        return RemoteSentPage(
            total =
                page.total,

            messages =
                page.messages.map {
                    message ->

                    RemoteSentMessage(
                        id =
                            message.id,

                        receiver =
                            message.receiver,

                        receiverAccountId =
                            message.receiverAccountId,

                        receiverUuid =
                            message.receiverUuid,

                        topic =
                            message.topic,

                        body =
                            message.body,

                        sendDateRaw =
                            message.sendDateRaw,

                        hasAttachment =
                            message.hasAttachment,

                        category =
                            message.category
                    )
                }
        )
    }
}
