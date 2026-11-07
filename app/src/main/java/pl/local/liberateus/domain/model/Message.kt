package pl.local.liberateus.domain.model

/**
 * Pełny logiczny model wiadomości.
 *
 * metadata zawsze istnieją.
 * content może być null, jeżeli treść nigdy nie została
 * jeszcze pobrana z serwera.
 */
data class Message(
    val metadata: InboxItem,
    val content: MessageContent?
) {
    val id: String
        get() = metadata.id

    val hasCachedContent: Boolean
        get() = content != null
}
