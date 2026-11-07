package pl.local.liberateus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun StatusText(
    status: String,
    busy: Boolean
) {

    val color:
        Color =
        when {

            status.startsWith(
                "Błąd",
                ignoreCase = true
            ) -> {
                MaterialTheme
                    .colorScheme
                    .error
            }

            status.contains(
                "nie udało",
                ignoreCase = true
            ) -> {
                MaterialTheme
                    .colorScheme
                    .error
            }

            status.contains(
                "Tryb lokalny",
                ignoreCase = true
            ) -> {
                MaterialTheme
                    .colorScheme
                    .tertiary
            }

            else -> {
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
            }
        }

    Row(
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(
                8.dp
            )
    ) {

        if (
            busy
        ) {
            CircularProgressIndicator(
                modifier =
                    Modifier.size(
                        16.dp
                    ),
                strokeWidth =
                    2.dp
            )
        }

        Text(
            text =
                status.ifBlank {
                    "Gotowe"
                },
            style =
                MaterialTheme
                    .typography
                    .bodySmall,
            color =
                color
        )
    }
}
