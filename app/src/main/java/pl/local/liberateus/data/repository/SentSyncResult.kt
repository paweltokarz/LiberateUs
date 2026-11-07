package pl.local.liberateus.data.repository

data class SentSyncResult(
    val fetchedCount: Int,
    val remoteTotal: Int,
    val completeSnapshot: Boolean,
    val syncedAt: Long
)
