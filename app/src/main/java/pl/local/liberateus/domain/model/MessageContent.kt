package pl.local.liberateus.domain.model

/**
 * Pełna treść wiadomości.
 *
 * Ten model istnieje dopiero wtedy, gdy użytkownik faktycznie
 * otworzył wiadomość i treść została pobrana z serwera
 * albo wcześniej znaleziona w lokalnym cache.
 */
data class MessageContent(
    val messageId: String,
    val content: String,
    val fetchedAt: Long
)
