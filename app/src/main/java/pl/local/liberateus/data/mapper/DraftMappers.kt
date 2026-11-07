package pl.local.liberateus.data.mapper

import org.json.JSONArray
import org.json.JSONObject
import pl.local.liberateus.data.local.DraftEntity
import pl.local.liberateus.data.remote.dto.RemoteDraft
import pl.local.liberateus.domain.model.Draft
import pl.local.liberateus.domain.model.DraftRecipient

fun RemoteDraft.toEntity(
    accountId: String,
    remoteOrder: Int,
    observedAt: Long
): DraftEntity =
    DraftEntity(
        accountId =
            accountId,
        draftId =
            id,
        ownerId =
            ownerId,
        parentMessageId =
            parentMessageId,
        dateAddedRaw =
            dateAddedRaw,
        deleted =
            deleted,
        hasAttachment =
            hasAttachment,
        topic =
            topic,
        body =
            body,
        receiversJson =
            receiversJson,
        type =
            type,
        remoteOrder =
            remoteOrder,
        lastSeenAt =
            observedAt
    )

fun DraftEntity.toDomain(): Draft =
    Draft(
        id =
            draftId,
        ownerId =
            ownerId,
        parentMessageId =
            parentMessageId,
        dateAddedRaw =
            dateAddedRaw,
        deleted =
            deleted,
        hasAttachment =
            hasAttachment,
        topic =
            topic,
        body =
            body,
        receivers =
            parseDraftRecipients(
                receiversJson
            ),
        type =
            type,
        remoteOrder =
            remoteOrder,
        lastSeenAt =
            lastSeenAt,
        receiversJson =
            receiversJson
    )

internal fun parseDraftRecipients(
    json: String
): List<DraftRecipient> {
    if (
        json.isBlank()
    ) {
        return emptyList()
    }

    return try {
        when {
            json.trimStart()
                .startsWith("[") ->
                parseRecipientArray(
                    JSONArray(
                        json
                    )
                )

            json.trimStart()
                .startsWith("{") -> {
                val root =
                    JSONObject(
                        json
                    )

                buildList {
                    val keys =
                        root.keys()

                    while (
                        keys.hasNext()
                    ) {
                        val key =
                            keys.next()

                        when (
                            val value =
                                root.opt(
                                    key
                                )
                        ) {
                            is JSONArray ->
                                addAll(
                                    parseRecipientArray(
                                        value
                                    )
                                )

                            is JSONObject ->
                                add(
                                    parseRecipientObject(
                                        value
                                    )
                                )
                        }
                    }
                }
            }

            else ->
                emptyList()
        }
    } catch (
        ignored: Throwable
    ) {
        emptyList()
    }
}

private fun parseRecipientArray(
    array: JSONArray
): List<DraftRecipient> =
    buildList {
        for (
            index in
            0 until array.length()
        ) {
            when (
                val value =
                    array.opt(
                        index
                    )
            ) {
                is JSONObject ->
                    add(
                        parseRecipientObject(
                            value
                        )
                    )

                is String ->
                    add(
                        DraftRecipient(
                            id =
                                null,
                            name =
                                value
                                    .trim()
                                    .takeIf {
                                        it.isNotEmpty()
                                    }
                        )
                    )
            }
        }
    }

private fun parseRecipientObject(
    obj: JSONObject
): DraftRecipient {
    val id =
        firstNonBlank(
            obj,
            "accountId",
            "identifier",
            "id",
            "receiverId",
            "uuid"
        )

    val directName =
        firstNonBlank(
            obj,
            "name",
            "fullName",
            "receiverName"
        )

    val firstName =
        firstNonBlank(
            obj,
            "firstName",
            "receiverFirstName"
        )

    val lastName =
        firstNonBlank(
            obj,
            "lastName",
            "receiverLastName"
        )

    val composedName =
        listOfNotNull(
            firstName,
            lastName
        )
            .joinToString(
                " "
            )
            .trim()
            .takeIf {
                it.isNotEmpty()
            }

    val previewName =
        if (obj.has("surname")) {
            val first =
                firstNonBlank(obj, "name").orEmpty()
            val surname =
                firstNonBlank(obj, "surname").orEmpty()

            if (surname.isNotBlank()) {
                if (
                    first.isNotBlank() &&
                    first.endsWith(
                        surname,
                        ignoreCase = true
                    )
                ) {
                    first
                } else {
                    listOf(first, surname)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                }
            } else {
                null
            }
        } else {
            null
        }

    return DraftRecipient(
        id =
            id,
        name =
            previewName
                ?: directName
                ?: composedName
    )
}

internal fun normalizeRecipientNamePart(value: String?): String? =
    value?.trim()?.takeIf {
        it.isNotEmpty() && !it.equals("null", ignoreCase = true)
    }

private fun firstNonBlank(
    obj: JSONObject,
    vararg keys: String
): String? {
    for (
        key in keys
    ) {
        if (
            !obj.has(key) ||
            obj.isNull(key)
        ) {
            continue
        }

        val value =
            obj.opt(
                key
            )
                ?.toString()?.let(::normalizeRecipientNamePart)

        if (
            value != null
        ) {
            return value
        }
    }

    return null
}
