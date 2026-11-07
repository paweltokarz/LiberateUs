package pl.local.liberateus.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_state"
)
data class SyncStateEntity(
    @PrimaryKey
    val accountId: String,

    /*
     * false:
     * pierwsza synchronizacja jest baseline'em
     * i nie generuje powiadomień.
     */
    val baselineInitialized: Boolean = false,

    val lastAttemptAt: Long? = null,
    val lastSuccessAt: Long? = null,

    /*
     * null po udanej synchronizacji.
     */
    val lastError: String? = null
)
