package pl.local.liberateus.data.remote

import pl.local.liberateus.data.remote.dto.DraftWritePayload
import pl.local.liberateus.data.remote.dto.DraftWriteResult

/**
 * Separate write boundary, independent of the
 * existing read-only DraftsRemoteDataSource.
 */
internal interface DraftWriteBackend {
    fun createDraft(
        payload: DraftWritePayload
    ): DraftWriteResult

    fun updateDraft(
        draftId: String,
        payload: DraftWritePayload
    ): DraftWriteResult

    fun deleteDraft(draftId: String)
}

class DraftWriteRemoteDataSource internal constructor(
    private val backend: DraftWriteBackend
) {
    internal constructor(
        connection: LibrusConnection
    ) : this(
        LibrusDraftWriteBackend(connection)
    )

    fun createDraft(
        payload: DraftWritePayload
    ): DraftWriteResult =
        backend.createDraft(payload)

    fun updateDraft(
        draftId: String,
        payload: DraftWritePayload
    ): DraftWriteResult {
        require(
            draftId.isNotBlank() &&
                draftId.all { it in '0'..'9' }
        ) {
            "Draft identifier must be numeric"
        }

        return backend.updateDraft(
            draftId,
            payload
        )
    }

    fun deleteDraft(draftId: String) {
        require(
            draftId.isNotBlank() &&
                draftId.all { it in '0'..'9' }
        ) {
            "Draft identifier must be numeric"
        }

        backend.deleteDraft(draftId)
    }
}

internal class LibrusDraftWriteBackend(
    connection: LibrusConnection
) : DraftWriteBackend {

    private val client =
        connection.legacyClient

    override fun createDraft(
        payload: DraftWritePayload
    ): DraftWriteResult =
        client.createDraft(payload)

    override fun updateDraft(
        draftId: String,
        payload: DraftWritePayload
    ): DraftWriteResult =
        client.updateDraft(
            draftId,
            payload
        )

    override fun deleteDraft(draftId: String) {
        client.deleteDraft(draftId)
    }
}
