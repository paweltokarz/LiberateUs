package pl.local.liberateus.ui.mailbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun MailboxSwitcher(
    selected: MailboxTab,
    onSelected: (MailboxTab) -> Unit
) {
    Row(
        modifier =
            Modifier.fillMaxWidth(),

        horizontalArrangement =
            Arrangement.spacedBy(
                6.dp
            )
    ) {
        MailboxButton(
            text =
                "Odebrane",

            selected =
                selected ==
                    MailboxTab.INBOX,

            modifier =
                Modifier.weight(1f),

            onClick = {
                onSelected(
                    MailboxTab.INBOX
                )
            }
        )

        MailboxButton(
            text =
                "Wysłane",

            selected =
                selected ==
                    MailboxTab.SENT,

            modifier =
                Modifier.weight(1f),

            onClick = {
                onSelected(
                    MailboxTab.SENT
                )
            }
        )

        MailboxButton(
            text =
                "Robocze",

            selected =
                selected ==
                    MailboxTab.DRAFTS,

            modifier =
                Modifier.weight(1f),

            onClick = {
                onSelected(
                    MailboxTab.DRAFTS
                )
            }
        )
    }
}

@Composable
private fun MailboxButton(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    if (
        selected
    ) {
        Button(
            modifier =
                modifier,

            onClick =
                onClick
        ) {
            Text(text)
        }
    } else {
        OutlinedButton(
            modifier =
                modifier,

            onClick =
                onClick
        ) {
            Text(text)
        }
    }
}
