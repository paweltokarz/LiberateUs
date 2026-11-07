package pl.local.liberateus.domain.model

/**
 * Wiadomość w lokalnym inboxie.
 *
 * To jest model aplikacji, a nie model Room i nie model API.
 *
 * Nie zawiera accountId celowo:
 * konto jest granicą repository, a nie właściwością potrzebną UI.
 */
data class InboxItem(
    val id: String,
    val sender: String,
    val topic: String,

    /**
     * Oryginalny tekst daty otrzymany z serwera.
     */
    val sendDateRaw: String,

    /**
     * Sparsowany timestamp, jeśli udało się go wiarygodnie ustalić.
     */
    val sentAtEpochMillis: Long?,

    val unread: Boolean,

    /**
     * Pozycja w ostatniej odpowiedzi remote.
     * 0 = pierwsza / najnowsza.
     */
    val remoteOrder: Int,

    /**
     * Kiedy Liberate Us po raz pierwszy zobaczył wiadomość.
     */
    val firstSeenAt: Long,

    /**
     * Kiedy ostatnio serwer potwierdził jej obecność.
     */
    val lastSeenAt: Long
)
