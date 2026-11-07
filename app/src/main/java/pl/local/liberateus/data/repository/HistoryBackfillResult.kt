package pl.local.liberateus.data.repository

/**
 * Result of a metadata-only history backfill.
 *
 * Backfill is intentionally separate from InboxSyncResult:
 *
 * - it does not establish a new-message baseline;
 * - it does not produce notification candidates;
 * - it does not fetch message bodies;
 * - it only extends/refreshes local Room history.
 */
data class HistoryBackfillResult(
    val fetchedCount: Int,
    val insertedCount: Int,
    val updatedCount: Int,
    val syncedAt: Long
)
