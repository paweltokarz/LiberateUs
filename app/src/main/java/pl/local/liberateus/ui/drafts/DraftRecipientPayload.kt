package pl.local.liberateus.ui.drafts

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * For drafts only. Never sends a message.
 */
internal fun buildSchoolRecipientsJson(
    ids: Collection<String>
): String {
    require(
        ids.all { id ->
            id.isNotEmpty() &&
                id.all { it in '0'..'9' }
        }
    )

    val members = ids
        .distinct()
        .joinToString(",") { id ->
            """{"accountId":"$id"}"""
        }

    return """{"schoolReceivers":[$members]}"""
}

/**
 * Returns null when saving could discard
 * unsupported recipient metadata.
 */
/**
 * Normalize an individual recipient account identifier.
 * Used for draft storage only.
 */
internal fun normalizeSchoolRecipientId(
    raw: String?
): String? =
    raw?.trim()?.takeIf { id ->
        id.isNotEmpty() &&
            id.all { it in '0'..'9' }
    }

internal fun editableSchoolRecipientIds(
    json: String
): List<String>? {
    if (json.isBlank()) return null

    return try {
        val value = JSONTokener(json).nextValue()

        /*
         * Librus returns a saved draft as an array
         * containing identifier, name, surname and
         * group metadata.
         *
         * Draft POST/PUT instead requires an object
         * containing schoolReceivers/accountId.
         */
        val fromDraftPreview = value is JSONArray

        val array = when (value) {
            is JSONArray ->
                value

            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    if (
                        keys.next() != "schoolReceivers"
                    ) {
                        return null
                    }
                }
                value.optJSONArray(
                    "schoolReceivers"
                ) ?: return null
            }

            else ->
                return null
        }

        val result = mutableListOf<String>()

        for (i in 0 until array.length()) {
            val item =
                array.optJSONObject(i) ?: return null

            val allowedKeys =
                if (fromDraftPreview) {
                    setOf(
                        "identifier",
                        "accountId",
                        "name",
                        "surname",
                        "groupIdentifier",
                        "groupName"
                    )
                } else {
                    setOf(
                        "accountId",
                        "name",
                        "fullName",
                        "receiverName",
                        "firstName",
                        "lastName"
                    )
                }

            val keys = item.keys()
            while (keys.hasNext()) {
                if (keys.next() !in allowedKeys) {
                    return null
                }
            }

            val rawAccountId =
                item.opt("accountId")
                    ?.toString()

            val rawIdentifier =
                item.opt("identifier")
                    ?.toString()

            /*
             * Reject conflicting identifiers rather
             * than silently choosing the wrong person.
             */
            if (
                fromDraftPreview &&
                rawAccountId != null &&
                rawIdentifier != null &&
                rawAccountId.trim() !=
                    rawIdentifier.trim()
            ) {
                return null
            }

            val candidate =
                if (fromDraftPreview) {
                    rawIdentifier ?: rawAccountId
                } else {
                    rawAccountId
                }

            val id =
                normalizeSchoolRecipientId(candidate)
                    ?: return null

            result.add(id)
        }

        result.distinct()
    } catch (_: Exception) {
        null
    }
}


/**
 * Human-readable summary for the draft recipient picker.
 *
 * Display-only: does not modify the outgoing draft JSON.
 */
internal fun selectedRecipientNames(
    ids: Collection<String>,
    knownNames: Map<String, String>
): String =
    ids.distinct().joinToString(", ") { id ->
        knownNames[id]
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "ID $id"
    }
