package pl.local.liberateus.ui.mailbox

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.local.liberateus.R
import pl.local.liberateus.ui.components.StatusText

@Composable
internal fun MailboxScaffold(
    username: String,
    status: String,
    busy: Boolean,
    selectedMailbox: MailboxTab,
    onMailboxSelected: (MailboxTab) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 20.dp,
                    vertical = 12.dp
                )
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth(),

            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Image(
                painter =
                    painterResource(
                        R.drawable
                            .ic_launcher_foreground
                    ),

                contentDescription =
                    null,

                modifier =
                    Modifier.size(
                        52.dp
                    )
            )

            Spacer(
                Modifier.size(
                    10.dp
                )
            )

            Row {
                Text(
                    modifier = Modifier.alignByBaseline(),
                    text =
                        "Liberate Us",
    
                    style =
                        MaterialTheme
                            .typography
                            .headlineMedium,
    
                    fontWeight =
                        FontWeight.Bold
                )
    
                Spacer(Modifier.size(7.dp))
    
                val appContext =
                    androidx.compose.ui.platform.LocalContext.current
                val versionName =
                    androidx.compose.runtime.remember(appContext) {
                        runCatching {
                            appContext.packageManager.getPackageInfo(
                                appContext.packageName, 0
                            ).versionName
                        }.getOrNull() ?: "?"
                    }
                Text(
                    modifier = Modifier.alignByBaseline(),
                    text = "v $versionName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(
            Modifier.height(
                8.dp
            )
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Column(
                modifier =
                    Modifier.weight(1f)
            ) {
                Text(
                    text =
                        "Konto",

                    style =
                        MaterialTheme
                            .typography
                            .labelMedium,

                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )

                Text(
                    text =
                        username.ifBlank {
                            "—"
                        },

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,

                    fontWeight =
                        FontWeight.SemiBold
                )
            }

            TextButton(
                onClick =
                    onLogout
            ) {
                Text(
                    "Wyloguj"
                )
            }
        }

        Spacer(
            Modifier.height(
                10.dp
            )
        )

        MailboxSwitcher(
            selected =
                selectedMailbox,

            onSelected =
                onMailboxSelected
        )

        Spacer(
            Modifier.height(
                10.dp
            )
        )

        if (false) {
Button(
            modifier =
                Modifier.fillMaxWidth(),

            enabled =
                !busy,

            onClick =
                onRefresh
        ) {
            Text(
                "Odśwież"
            )
        }
            }

        Spacer(
            Modifier.height(
                7.dp
            )
        )

        StatusText(
            status =
                status,

            busy =
                busy
        )

        Spacer(
            Modifier.height(
                12.dp
            )
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
        ) {
            content()
        }
    }
}
