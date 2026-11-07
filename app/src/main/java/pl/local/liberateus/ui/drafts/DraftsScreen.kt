package pl.local.liberateus.ui.drafts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pl.local.liberateus.data.remote.dto.DraftRecipientGroup
import pl.local.liberateus.data.remote.dto.SelectableDraftRecipient
import pl.local.liberateus.domain.model.Draft
import pl.local.liberateus.ui.mailbox.EmptyMailboxCard
import pl.local.liberateus.ui.mailbox.MailboxContentHeader
import pl.local.liberateus.ui.mailbox.MailboxMessageCard

internal data class ReplyDraftPrefill(
    val messageId: String,
    val senderName: String,
    val topic: String,
    val content: String,
    val receiversJson: String,
    val recipientNames: Map<String, String>
)

@Composable
internal fun DraftsContent(
    drafts: List<Draft>,
    expandedDraftId: String?,
    onToggleDraft: (String) -> Unit,
    replyPrefill: ReplyDraftPrefill? = null,
    onDismissReply: () -> Unit = {},
    onLoadRecipientTypes: suspend () ->
        List<DraftRecipientGroup>,
    onLoadRecipients: suspend (
        DraftRecipientGroup
    ) -> List<SelectableDraftRecipient>,
    onSaveNewDraft: suspend (
        String, String, String
    ) -> Unit,
    onUpdateDraft: suspend (
        String, String, String, String
    ) -> Unit,
    onDeleteDraft: suspend (String) -> Unit,
    onSendMessage: (suspend (String, String, String, String?) -> Unit)? = null
) {
    val newEditor = remember {
        mutableStateOf(false)
    }
    val editing = remember {
        mutableStateOf<Draft?>(null)
    }
    val pendingDelete = remember {
        mutableStateOf<Draft?>(null)
    }
    val deleting = remember {
        mutableStateOf(false)
    }
    val deleteAttempted = remember {
        mutableStateOf(false)
    }
    val deleteError = remember {
        mutableStateOf<String?>(null)
    }
    val savedNotice = remember {
        mutableStateOf(false)
    }
    val deletedNotice = remember {
        mutableStateOf(false)
    }

    val scope = rememberCoroutineScope()

    val target = pendingDelete.value

    if (target != null) {
        AlertDialog(
            onDismissRequest = {
                if (!deleting.value) {
                    pendingDelete.value = null
                }
            },
            title = {
                Text("Usunąć roboczą?")
            },
            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Robocza „" +
                            target.topic.ifBlank {
                                "(bez tematu)"
                            } +
                            "” zostanie usunięta z Librusa."
                    )

                    if (deleteError.value != null) {
                        Text(deleteError.value.orEmpty())
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled =
                        !deleting.value &&
                            !deleteAttempted.value,
                    onClick = {
                        if (!deleteAttempted.value) {
                            deleteAttempted.value = true
                            deleting.value = true
                            deleteError.value = null

                            scope.launch {
                                try {
                                    onDeleteDraft(target.id)
                                    pendingDelete.value = null
                                    savedNotice.value = false
                                    deletedNotice.value = true
                                } catch (
                                    cancelled: CancellationException
                                ) {
                                    throw cancelled
                                } catch (
                                    error: Exception
                                ) {
                                    deleteError.value =
                                        "Nie udało się potwierdzić " +
                                            "usunięcia. Odśwież Robocze " +
                                            "przed ponowieniem. " +
                                            error.message
                                                .orEmpty()
                                                .take(160)
                                } finally {
                                    deleting.value = false
                                }
                            }
                        }
                    }
                ) {
                    Text("Usuń")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !deleting.value,
                    onClick = {
                        pendingDelete.value = null
                    }
                ) {
                    Text(
                        if (deleteAttempted.value) {
                            "Zamknij"
                        } else {
                            "Anuluj"
                        }
                    )
                }
            }
        )
    }

    if (replyPrefill != null) {
        key(replyPrefill.messageId) {
            Column {
                if (replyPrefill.recipientNames.isEmpty()) {
                    Text(
                        "Nie udało się jednoznacznie ustalić odbiorcy: " +
                            replyPrefill.senderName +
                            ". Wybierz go z katalogu adresatów."
                    )
                }
                DraftComposeEditor(
                    initialTopic = replyPrefill.topic,
                    initialContent = replyPrefill.content,
                    initialReceiversJson = replyPrefill.receiversJson,
                    initialRecipientNames = replyPrefill.recipientNames,
                    onLoadRecipientTypes = onLoadRecipientTypes,
                    onLoadRecipients = onLoadRecipients,
                    onDismiss = onDismissReply,
                    onSend = if (onSendMessage != null) {
                        { topic: String, content: String, receivers: String ->
                            onSendMessage(topic, content, receivers, null)
                        }
                    } else null,
                    onSave = { topic, content, receivers ->
                        onSaveNewDraft(topic, content, receivers)
                        onDismissReply()
                    }
                )
            }
        }
    } else if (editing.value != null) {
        val draft = editing.value!!

        val supportedReceivers =
            editableSchoolRecipientIds(
                draft.receiversJson
            ) != null

        val canSafelySave =
            supportedReceivers &&
                canEditDraftSafely(
                    draftId = draft.id,
                    type = draft.type,
                    recipientCount = draft.receivers.size,
                    hasAttachment = draft.hasAttachment,
                    allowKnownSchoolRecipients =
                        supportedReceivers
                )

        key(draft.id) {
            DraftComposeEditor(
                selfRecipientAccountId =
                    inferSelfRecipientId(drafts.map { it.ownerId }),

                initialTopic = draft.topic,
                initialContent = draft.body,
                initialReceiversJson = draft.receiversJson,
                initialRecipientNames =
                    draft.receivers
                        .mapNotNull { recipient ->
                            val id =
                                recipient.id?.trim()
                            val name =
                                recipient.name?.trim()

                            if (
                                !id.isNullOrEmpty() &&
                                !name.isNullOrEmpty()
                            ) {
                                id to name
                            } else {
                                null
                            }
                        }
                        .toMap(),
                editorTitle = "Edytuj roboczą",
                saveAllowed = canSafelySave,
                onLoadRecipientTypes = onLoadRecipientTypes,
                onLoadRecipients = onLoadRecipients,
                onDismiss = {
                    editing.value = null
                },
                onSend = if (onSendMessage != null) {
                    { topic: String, content: String, receivers: String ->
                        onSendMessage(topic, content, receivers, draft.id)
                    }
                } else null,
                onSave = { topic, content, receiversJson ->
                    onUpdateDraft(
                        draft.id,
                        topic,
                        content,
                        receiversJson
                    )
                    savedNotice.value = true
                    deletedNotice.value = false
                }
            )
        }
    } else if (newEditor.value) {
        DraftComposeEditor(
                selfRecipientAccountId =
                    inferSelfRecipientId(drafts.map { it.ownerId }),

            onLoadRecipientTypes = onLoadRecipientTypes,
                onLoadRecipients = onLoadRecipients,
            onDismiss = {
                newEditor.value = false
            },
            onSend = if (onSendMessage != null) {
                    { topic: String, content: String, receivers: String ->
                        onSendMessage(topic, content, receivers, null)
                    }
                } else null,
                onSave = { topic, content, receiversJson ->
                onSaveNewDraft(
                    topic,
                    content,
                    receiversJson
                )
                savedNotice.value = true
                deletedNotice.value = false
            }
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(bottom = 16.dp),
            verticalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            item {
                MailboxContentHeader(
                    title = "Robocze",
                    count = drafts.size
                )
            }

            item {
                OutlinedButton(
                    onClick = {
                        savedNotice.value = false
                        deletedNotice.value = false
                        newEditor.value = true
                    }
                ) {
                    Text("Nowa robocza")
                }
            }

            if (savedNotice.value) {
                item {
                    Text(
                        "Zapisano szkic. Jeśli nie ma go " +
                            "na liście, odśwież Robocze."
                    )
                }
            }

            if (deletedNotice.value) {
                item {
                    Text(
                        "Usunięto roboczą. Jeśli nadal " +
                            "jest widoczna, odśwież Robocze."
                    )
                }
            }

            if (drafts.isEmpty()) {
                item {
                    EmptyMailboxCard(
                        text = "Brak zapisanych roboczych."
                    )
                }
            } else {
                items(
                    items = drafts,
                    key = { it.id }
                ) { draft ->

                    val receiver =
                        draft.receivers
                            .mapNotNull { it.name }
                            .filter { it.isNotBlank() }
                            .joinToString(", ")
                            .ifBlank { "Bez odbiorcy" }

                    MailboxMessageCard(
                        actorPrefix = "Do",
                        actor = receiver,
                        topic = draft.topic,
                        dateRaw = null,
                        bodyHtml = draft.body,
                        expanded =
                            expandedDraftId == draft.id,
                        loading = false,
                        unread = false,
                        hasAttachment = draft.hasAttachment,
                        onClick = {
                            onToggleDraft(draft.id)
                        },
                        actions = {
                            OutlinedButton(
                                onClick = {
                                    savedNotice.value = false
                                    deletedNotice.value = false
                                    editing.value = draft
                                }
                            ) {
                                Text("Edytuj")
                            }

                            OutlinedButton(
                                onClick = {
                                    pendingDelete.value = draft
                                    deleteAttempted.value = false
                                    deleteError.value = null
                                }
                            ) {
                                Text("Usuń")
                            }
                        }
                    )
                }
            }
        }
    }
}
