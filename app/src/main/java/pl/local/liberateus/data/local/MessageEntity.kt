package pl.local.liberateus.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "messages",
    primaryKeys = [
        "accountId",
        "messageId"
    ],
    indices = [
        Index(
            value = [
                "accountId",
                "lastSeenAt"
            ]
        ),
        Index(
            value = [
                "accountId",
                "unread"
            ]
        )
    ]
)
data class MessageEntity(
    val accountId: String,
    val messageId: String,

    val sender: String,
    val topic: String,

    /*
     * Zachowujemy dokładnie tekst daty otrzymany z API.
     * Parsowanie do czasu maszynowego zrobimy dopiero,
     * gdy ustalimy wszystkie formaty zwracane przez Librusa.
     */
    val sendDateRaw: String,

    /*
     * Nullable celowo.
     * W przyszłości mapper API może tu zapisać poprawnie
     * sparsowany timestamp bez migracji tabeli.
     */
    val sentAtEpochMillis: Long? = null,

    /*
     * Stan widziany podczas ostatniej synchronizacji.
     */
    val unread: Boolean,

    /*
     * Pozycja wiadomości w ostatniej odpowiedzi serwera.
     * 0 = pierwsza / najnowsza w danej odpowiedzi.
     *
     * W połączeniu z lastSeenAt pozwala zachować kolejność
     * także zanim zaczniemy niezawodnie parsować daty.
     */
    val remoteOrder: Int,

    /*
     * Moment pierwszego zauważenia wiadomości lokalnie.
     * Nigdy go później nie nadpisujemy.
     */
    val firstSeenAt: Long,

    /*
     * Moment ostatniego potwierdzenia obecności wiadomości
     * w odpowiedzi serwera.
     */
    val lastSeenAt: Long
)
