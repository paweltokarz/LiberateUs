package pl.local.liberateus.data.remote

import pl.local.liberateus.data.remote.dto.RemoteDraft
import pl.local.liberateus.data.remote.dto.RemoteDraftPage

internal interface DraftsRemoteBackend {

    fun getDrafts(
        limit: Int
    ): RemoteDraftPage
}

class DraftsRemoteDataSource internal constructor(
    private val backend:
        DraftsRemoteBackend
) {

    internal constructor(
        connection: LibrusConnection
    ) : this(
        LibrusDraftsBackend(
            connection
        )
    )

    fun getDrafts(
        limit: Int = 100
    ): RemoteDraftPage {
        require(
            limit > 0
        )

        return backend.getDrafts(
            limit
        )
    }
}

internal class LibrusDraftsBackend(
    connection: LibrusConnection
) : DraftsRemoteBackend {

    private val client =
        connection.legacyClient

    override fun getDrafts(
        limit: Int
    ): RemoteDraftPage {
        val page =
            client.fetchDrafts(
                limit
            )

        return RemoteDraftPage(
            total =
                page.total,
            drafts =
                page.drafts.map {
                    draft ->

                    RemoteDraft(
                        id =
                            draft.id,
                        ownerId =
                            draft.ownerId,
                        parentMessageId =
                            draft.parentMessageId,
                        dateAddedRaw =
                            draft.dateAddedRaw,
                        deleted =
                            draft.deleted,
                        hasAttachment =
                            draft.hasAttachment,
                        topic =
                            draft.topic,
                        body =
                            draft.body,
                        receiversJson =
                            draft.receiversJson,
                        type =
                            draft.type
                    )
                }
        )
    }
}
