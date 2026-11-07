package pl.local.liberateus.data.repository

data class DraftSyncResult(
    val fetchedCount: Int,
    val remoteTotal: Int,
    val completeSnapshot: Boolean,
    val syncedAt: Long
)
