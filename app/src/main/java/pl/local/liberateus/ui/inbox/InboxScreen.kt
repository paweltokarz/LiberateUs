package pl.local.liberateus.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.local.liberateus.domain.model.InboxItem
import pl.local.liberateus.domain.model.Message
import pl.local.liberateus.ui.mailbox.EmptyMailboxCard
import pl.local.liberateus.ui.mailbox.MailboxContentHeader
import pl.local.liberateus.ui.mailbox.MailboxMessageCard

@Composable
internal fun InboxContent(
    messages: List<InboxItem>,
    expandedMessage: Message?,
    openingMessageId: String?,
    onOpenMessage: (InboxItem) -> Unit,
    onReply: (Message) -> Unit
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
                    "Odebrane wiadomości",

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
                        "Brak zapisanych wiadomości."
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

                val expanded =
                    expandedMessage
                        ?.takeIf {
                            it.id ==
                                message.id
                        }

                MailboxMessageCard(
                    actorPrefix =
                        "Od",

                    actor =
                        message.sender,

                    topic =
                        message.topic,

                    dateRaw =
                        message.sendDateRaw,

                    bodyHtml =
                        expanded
                            ?.content
                            ?.content,

                    expanded =
                        expanded != null,

                    loading =
                        openingMessageId ==
                            message.id,

                    unread =
                        message.unread,

                    hasAttachment =
                        false,

                    onClick = {
                        onOpenMessage(
                            message
                        )
                    },
                    actions = if (expanded != null) {
                        {
                            TextButton(onClick = { onReply(expanded) }) {
                                Text("Odpowiedz")
                            }
                        }
                    } else null
                )
            }
        }
    }
}
