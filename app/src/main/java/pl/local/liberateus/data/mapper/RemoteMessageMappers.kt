package pl.local.liberateus.data.mapper

import pl.local.liberateus.data.remote.dto.RemoteFullMessage
import pl.local.liberateus.data.remote.dto.RemoteInboxMessage
import pl.local.liberateus.domain.model.InboxItem
import pl.local.liberateus.domain.model.MessageContent

fun RemoteInboxMessage.toDomain(
    remoteOrder: Int,
    observedAt: Long,
    firstSeenAt: Long = observedAt
): InboxItem {

    return InboxItem(
        id = id,
        sender = sender,
        topic = topic,
        sendDateRaw = sendDate,
        sentAtEpochMillis = null,
        unread = unread,
        remoteOrder = remoteOrder,
        firstSeenAt = firstSeenAt,
        lastSeenAt = observedAt
    )
}

/**
 * A successful full-message fetch means the message
 * should be considered read locally as well.
 */
fun RemoteFullMessage.toInboxDomain(
    remoteOrder: Int,
    observedAt: Long,
    firstSeenAt: Long = observedAt
): InboxItem {

    return InboxItem(
        id = id,
        sender = sender,
        topic = topic,
        sendDateRaw = sendDate,
        sentAtEpochMillis = null,
        unread = false,
        remoteOrder = remoteOrder,
        firstSeenAt = firstSeenAt,
        lastSeenAt = observedAt
    )
}

fun RemoteFullMessage.toContentDomain(
    fetchedAt: Long
): MessageContent {

    return MessageContent(
        messageId = id,
        content = content,
        fetchedAt = fetchedAt
    )
}
