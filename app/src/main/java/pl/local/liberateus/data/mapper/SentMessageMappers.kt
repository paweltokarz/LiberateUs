package pl.local.liberateus.data.mapper

import pl.local.liberateus.data.local.SentMessageEntity
import pl.local.liberateus.data.remote.dto.RemoteSentMessage
import pl.local.liberateus.domain.model.SentMessage

fun RemoteSentMessage.toEntity(
    accountId: String,
    remoteOrder: Int,
    observedAt: Long
): SentMessageEntity =
    SentMessageEntity(
        accountId =
            accountId,

        messageId =
            id,

        receiver =
            receiver,

        receiverAccountId =
            receiverAccountId,

        receiverUuid =
            receiverUuid,

        topic =
            topic,

        body =
            body,

        sendDateRaw =
            sendDateRaw,

        hasAttachment =
            hasAttachment,

        category =
            category,

        remoteOrder =
            remoteOrder,

        lastSeenAt =
            observedAt
    )

fun SentMessageEntity.toDomain():
    SentMessage =
    SentMessage(
        id =
            messageId,

        receiver =
            receiver,

        receiverAccountId =
            receiverAccountId,

        receiverUuid =
            receiverUuid,

        topic =
            topic,

        body =
            body,

        sendDateRaw =
            sendDateRaw,

        hasAttachment =
            hasAttachment,

        category =
            category,

        remoteOrder =
            remoteOrder,

        lastSeenAt =
            lastSeenAt
    )
