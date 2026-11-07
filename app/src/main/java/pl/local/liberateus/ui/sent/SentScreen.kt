package pl.local.liberateus.ui.sent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.local.liberateus.domain.model.SentMessage
import pl.local.liberateus.ui.mailbox.EmptyMailboxCard
import pl.local.liberateus.ui.mailbox.MailboxContentHeader
import pl.local.liberateus.ui.mailbox.MailboxMessageCard

@Composable
internal fun SentContent(
    messages: List<SentMessage>,
    expandedMessageId: String?,
    onToggleMessage: (String) -> Unit,
    fullBodies: Map<String, String> = emptyMap(),
    loadingMessageId: String? = null,
    detailError: String? = null
) {
    LazyColumn(
        modifier =
            Modifier.fillMaxSize(),

        contentPadding =
            PaddingValues(
                bottom = 16.dp
            ),

        verticalArrangement =
            Arrangement.spacedBy(
                10.dp
            )
    ) {
        item {
            MailboxContentHeader(
                title =
                    "Wysłane wiadomości",

                count =
                    messages.size
            )
        }

        if (
            messages.isEmpty()
        ) {
            item {
                EmptyMailboxCard(
                    text =
                        "Brak zapisanych wysłanych wiadomości."
                )
            }

        } else {
            items(
                items =
                    messages,

                key = {
                    it.id
                }
            ) {
                message ->

                Column {
                MailboxMessageCard(
                    actorPrefix =
                        "Do",

                    actor =
                        message.receiver,

                    topic =
                        message.topic,

                    dateRaw =
                        message.sendDateRaw,

                    bodyHtml =
                        fullBodies[message.id].orEmpty(),

                    expanded =
                        expandedMessageId ==
                            message.id,

                    loading =
                        loadingMessageId == message.id,

                    unread =
                        false,

                    hasAttachment =
                        message.hasAttachment,

                    onClick = {
                        onToggleMessage(
                            message.id
                        )
                    }
                )
                if (
                    expandedMessageId == message.id &&
                    detailError != null
                ) {
                    Text(
                        text = detailError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = { onToggleMessage(message.id) }) {
                        Text("Spróbuj ponownie")
                    }
                }
                }
            }
        }
    }
}
