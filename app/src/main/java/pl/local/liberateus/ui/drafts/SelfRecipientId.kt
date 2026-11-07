package pl.local.liberateus.ui.drafts

internal fun inferSelfRecipientId(ownerIds: List<String?>): String? =
    ownerIds.mapNotNull { value ->
        value?.trim()?.takeIf { id ->
            id.isNotEmpty() && id.all { it.isDigit() }
        }
    }.distinct().singleOrNull()
