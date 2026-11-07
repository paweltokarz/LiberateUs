package pl.local.liberateus.data.repository

import pl.local.liberateus.domain.model.InboxItem

/**
 * Result of one inbox metadata synchronization.
 *
 * insertedCount:
 *   technical number of Room rows that did not exist before
 *   this synchronization.
 *
 * newMessages:
 *   messages that are semantically new relative to an
 *   already initialized baseline.
 *
 * On the first successful synchronization all fetched rows
 * establish the baseline and newMessages is deliberately empty.
 */
data class InboxSyncResult(

    val fetchedCount: Int,

    val insertedCount: Int,

    val updatedCount: Int,

    /**
     * true only for the successful synchronization that
     * establishes the account baseline.
     */
    val wasBaselineSync: Boolean,

    /**
     * New metadata rows discovered after baseline creation.
     *
     * Order is the same as the remote inbox page.
     * Message bodies are never fetched to populate this list.
     */
    val newMessages:
        List<InboxItem>,

    val syncedAt: Long
) {

    val newMessageCount: Int
        get() =
            newMessages.size
}
