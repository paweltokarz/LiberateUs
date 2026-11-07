package pl.local.liberateus.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SyncStateDao {

    @Query(
        """
        SELECT *
        FROM sync_state
        WHERE accountId = :accountId
        LIMIT 1
        """
    )
    suspend fun get(
        accountId: String
    ): SyncStateEntity?

    @Upsert
    suspend fun upsert(
        state: SyncStateEntity
    )

    @Query(
        """
        DELETE FROM sync_state
        WHERE accountId = :accountId
        """
    )
    suspend fun delete(
        accountId: String
    )
}
