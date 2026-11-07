package pl.local.liberateus.data.mapper

import pl.local.liberateus.data.local.MessageContentEntity
import pl.local.liberateus.data.local.MessageEntity
import pl.local.liberateus.domain.model.InboxItem
import pl.local.liberateus.domain.model.Message
import pl.local.liberateus.domain.model.MessageContent

/**
 * Room -> domain
 */
fun MessageEntity.toDomain():
    InboxItem {

    return InboxItem(
        id = messageId,
        sender = sender,
        topic = topic,
        sendDateRaw = sendDateRaw,
        sentAtEpochMillis =
            sentAtEpochMillis,
        unread = unread,
        remoteOrder = remoteOrder,
        firstSeenAt = firstSeenAt,
        lastSeenAt = lastSeenAt
    )
}

/**
 * Domain -> Room
 *
 * accountId jest przekazywane jawnie.
 * Model domenowy nie powinien wiedzieć,
 * w jaki sposób dane są partycjonowane w Room.
 */
fun InboxItem.toEntity(
    accountId: String
): MessageEntity {

    require(
        accountId.isNotBlank()
    ) {
        "accountId must not be blank"
    }

    require(
        id.isNotBlank()
    ) {
        "message id must not be blank"
    }

    return MessageEntity(
        accountId = accountId,
        messageId = id,
        sender = sender,
        topic = topic,
        sendDateRaw = sendDateRaw,
        sentAtEpochMillis =
            sentAtEpochMillis,
        unread = unread,
        remoteOrder = remoteOrder,
        firstSeenAt = firstSeenAt,
        lastSeenAt = lastSeenAt
    )
}

/**
 * Room content -> domain content
 */
fun MessageContentEntity.toDomain():
    MessageContent {

    return MessageContent(
        messageId = messageId,
        content = content,
        fetchedAt = fetchedAt
    )
}

/**
 * Domain content -> Room content
 */
fun MessageContent.toEntity(
    accountId: String
): MessageContentEntity {

    require(
        accountId.isNotBlank()
    ) {
        "accountId must not be blank"
    }

    require(
        messageId.isNotBlank()
    ) {
        "messageId must not be blank"
    }

    return MessageContentEntity(
        accountId = accountId,
        messageId = messageId,
        content = content,
        fetchedAt = fetchedAt
    )
}

/**
 * Łączy rekord metadata i opcjonalny rekord treści
 * w pełny model domenowy.
 *
 * Celowo pilnujemy zgodności kluczy, żeby przypadkowo
 * nie połączyć body wiadomości A z metadata wiadomości B.
 */
fun MessageEntity.toDomainMessage(
    contentEntity:
        MessageContentEntity?
): Message {

    if (contentEntity != null) {

        require(
            contentEntity.accountId ==
                accountId
        ) {
            "Content belongs to another account"
        }

        require(
            contentEntity.messageId ==
                messageId
        ) {
            "Content belongs to another message"
        }
    }

    return Message(
        metadata = toDomain(),
        content =
            contentEntity?.toDomain()
    )
}
