package pl.local.liberateus.ui.message

import android.text.Html
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.local.liberateus.domain.model.Message
import pl.local.liberateus.ui.components.formatMessageDate

@Composable
internal fun DirectMessageScreen(
    message: Message,
    onBack: () -> Unit
) {

    val date =
        formatMessageDate(
            message
                .metadata
                .sendDateRaw
        )

    val body =
        Html.fromHtml(
            message
                .content
                ?.content
                .orEmpty(),
            Html.FROM_HTML_MODE_LEGACY
        )
            .toString()
            .trim()

    LazyColumn(
        modifier =
            Modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout.PaddingValues(
                horizontal =
                    20.dp,
                vertical =
                    16.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                12.dp
            )
    ) {

        item {

            TextButton(
                onClick =
                    onBack
            ) {
                Text(
                    "← Wstecz"
                )
            }
        }

        item {

            Text(
                text =
                    message
                        .metadata
                        .sender
                        .ifBlank {
                            "Nieznany nadawca"
                        },
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                fontWeight =
                    FontWeight.SemiBold,
                color =
                    MaterialTheme
                        .colorScheme
                        .primary
            )
        }

        item {

            Text(
                text =
                    message
                        .metadata
                        .topic
                        .ifBlank {
                            "(bez tematu)"
                        },
                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,
                fontWeight =
                    FontWeight.Bold
            )
        }

        if (
            date.isNotBlank()
        ) {

            item {
                Text(
                    text =
                        date,
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )
            }
        }

        item {

            HorizontalDivider()
        }

        item {

            Card(
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(
                        18.dp
                    ),
                colors =
                    CardDefaults
                        .cardColors(
                            containerColor =
                                MaterialTheme
                                    .colorScheme
                                    .surfaceVariant
                                    .copy(
                                        alpha =
                                            0.32f
                                    )
                        )
            ) {

                Text(
                    text =
                        body.ifBlank {
                            "Brak treści wiadomości."
                        },
                    modifier =
                        Modifier.padding(
                            18.dp
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .bodyLarge
                )
            }
        }
    }
}

@Composable
internal fun MessageLoadingScreen() {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    24.dp
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center
    ) {

        CircularProgressIndicator()

        Spacer(
            Modifier.height(
                16.dp
            )
        )

        Text(
            text =
                "Otwieranie wiadomości…",
            style =
                MaterialTheme
                    .typography
                    .bodyLarge
        )
    }
}

@Composable
internal fun MessageErrorScreen(
    message: String,
    onBack: () -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    24.dp
                ),
        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text =
                "Nie udało się otworzyć wiadomości",
            style =
                MaterialTheme
                    .typography
                    .headlineSmall,
            fontWeight =
                FontWeight.Bold,
            color =
                MaterialTheme
                    .colorScheme
                    .error
        )

        Spacer(
            Modifier.height(
                10.dp
            )
        )

        Text(
            text =
                message,
            style =
                MaterialTheme
                    .typography
                    .bodyMedium
        )

        Spacer(
            Modifier.height(
                20.dp
            )
        )

        Button(
            onClick =
                onBack
        ) {
            Text(
                "Wróć"
            )
        }
    }
}
