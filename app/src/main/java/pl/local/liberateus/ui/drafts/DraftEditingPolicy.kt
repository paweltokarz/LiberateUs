package pl.local.liberateus.ui.drafts

/**
 * Restrict draft editing to the subset currently
 * representable by DraftWritePayload.
 *
 * In particular, never overwrite recipient lists
 * or attachment references with empty defaults.
 */
internal fun canEditDraftSafely(
    draftId: String,
    type: String?,
    recipientCount: Int,
    hasAttachment: Boolean,
    allowKnownSchoolRecipients: Boolean = false
): Boolean =
    draftId.isNotBlank() &&
        draftId.all { it in '0'..'9' } &&
        type.equals("NEWONE", ignoreCase = true) &&
        (
            recipientCount == 0 ||
                allowKnownSchoolRecipients
        ) &&
        !hasAttachment
