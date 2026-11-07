package pl.local.liberateus.data.remote.dto

/**
 * Metadata returned by the inbox-list endpoint.
 *
 * Loading this object must never require downloading
 * the full message body.
 */
data class RemoteInboxMessage(
    val id: String,
    val sender: String,
    val topic: String,

    /*
     * Nazwa sendDate jest celowo zgodna z dotychczasowym
     * modelem UI. Mapper domenowy tłumaczy ją później
     * na sendDateRaw.
     */
    val sendDate: String,

    val unread: Boolean
)

/**
 * Full remote message.
 *
 * Librus treats fetching the full message as opening it,
 * so this object must never be prefetched by inbox sync.
 */
data class RemoteFullMessage(
    val id: String,
    val sender: String,
    val topic: String,
    val sendDate: String,
    val content: String
)
