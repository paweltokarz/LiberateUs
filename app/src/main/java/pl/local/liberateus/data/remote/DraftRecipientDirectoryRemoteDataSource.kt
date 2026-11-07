package pl.local.liberateus.data.remote

import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject
import pl.local.liberateus.data.remote.dto.DraftRecipientGroup
import pl.local.liberateus.data.remote.dto.SelectableDraftRecipient
import pl.local.liberateus.data.remote.dto.recipientGroupEndpoint
import pl.local.liberateus.data.remote.dto.recipientTypeQuery

/**
 * Read-only recipient tree for saving drafts.
 * No message sending functionality.
 */
class DraftRecipientDirectoryRemoteDataSource internal constructor(
    connection: LibrusConnection
) {
    private val client = connection.legacyClient

    fun getRecipientTypes(): List<DraftRecipientGroup> {
        val json = JSONObject(
            client.fetchDraftRecipientTypes()
        )
        val types = json.optJSONObject("data")
            ?.optJSONArray("list")
            ?: throw IOException(
                "Nieznany format kategorii odbiorców"
            )

        val groups = mutableListOf<DraftRecipientGroup>()

        for (i in 0 until types.length()) {
            val item = types.optJSONObject(i) ?: continue
            val id = item.optString("id").trim()
            val path = recipientGroupEndpoint(id)
                ?: continue
            val name = item.optString("name")
                .trim()
                .ifBlank {
                    item.optString("label").trim()
                }

            if (name.isNotBlank()) {
                groups.add(
                    DraftRecipientGroup(id, name, path)
                )
            }
        }
        return groups.distinctBy { it.id }
    }

    fun getRecipients(
        group: DraftRecipientGroup
    ): List<SelectableDraftRecipient> {
        val path = recipientGroupEndpoint(group.id)
            ?: throw IOException(
                "Unsupported recipient group"
            )
        require(path == group.path)

        val response = JSONObject(
            client.fetchDraftRecipientGroup(
                path,
                recipientTypeQuery(group.id)
            )
        )
        /*
         * Librus returns recipient categories as:
         *   { "data": { "list": [...] } }
         *
         * Individual recipient groups are normally:
         *   { "receivers": [...] }
         *
         * Some categories may use a data wrapper,
         * so accept both shapes.
         */
        val data =
            response.optJSONObject("data") ?: response

        if (
            !data.has("receivers") &&
            !data.has("classes") &&
            !data.has("data") &&
            !data.has("groups")
        ) {
            throw IOException(
                "Nieznany format grupy odbiorców"
            )
        }

        val people = linkedMapOf<
            String, SelectableDraftRecipient
        >()

        fun addPerson(
            item: JSONObject,
            classLabel: String? = null
        ) {
            val id = item.opt("accountId")
                ?.toString()?.trim().orEmpty()
            if (
                id.isBlank() ||
                id.any { it !in '0'..'9' }
            ) return

            val name = listOf(
                "label", "name", "fullName",
                "displayName", "receiverName"
            ).map {
                item.optString(it).trim()
            }.firstOrNull {
                it.isNotBlank() && it != "null"
            } ?: listOf(
                item.optString("firstName"),
                item.optString("lastName")
            ).filter {
                it.isNotBlank()
            }.joinToString(" ")

            if (name.isBlank()) return
            if (item.optBoolean("disabled", false)) return

            people.putIfAbsent(
                id,
                SelectableDraftRecipient(
                    id, name, classLabel
                )
            )
        }

        fun consume(
            array: JSONArray?,
            classLabel: String? = null
        ) {
            if (array == null) return
            for (i in 0 until array.length()) {
                val value = array.opt(i)
                when (value) {
                    is JSONObject ->
                        addPerson(value, classLabel)
                    is JSONArray ->
                        consume(value, classLabel)
                }
            }
        }

        consume(data.optJSONArray("receivers"))

        // Class-structured categories can return
        // classes with nested receivers.
        val classes = data.optJSONArray("classes")
            ?: data.optJSONArray("data")

        if (classes != null) {
            for (i in 0 until classes.length()) {
                val item = classes.optJSONObject(i)
                    ?: continue
                consume(
                    item.optJSONArray("receivers"),
                    item.optString("label")
                        .trim()
                        .takeIf { it.isNotEmpty() }
                )
            }
        }

        return people.values.toList()
    }
}
