package pl.local.liberateus.ui.mailbox

import android.text.Html
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Composable
internal fun MailboxContentHeader(
    title: String,
    count: Int
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    bottom = 10.dp
                ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Text(
            text =
                title,

            modifier =
                Modifier.weight(1f),

            style =
                MaterialTheme
                    .typography
                    .titleLarge,

            fontWeight =
                FontWeight.SemiBold
        )

        Text(
            text =
                count.toString(),

            style =
                MaterialTheme
                    .typography
                    .labelLarge,

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )
    }
}

@Composable
internal fun EmptyMailboxCard(
    text: String
) {
    Card(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(
                16.dp
            )
    ) {
        Text(
            text =
                text,

            modifier =
                Modifier.padding(
                    18.dp
                ),

            style =
                MaterialTheme
                    .typography
                    .bodyMedium
        )
    }
}

@Composable
internal fun MailboxMessageCard(
    actorPrefix: String,
    actor: String,
    topic: String,
    dateRaw: String?,
    bodyHtml: String?,
    expanded: Boolean,
    loading: Boolean,
    unread: Boolean,
    hasAttachment: Boolean,
    onClick: () -> Unit,
    actions: (@Composable () -> Unit)? = null
) {
    val plainBody =
        bodyHtml
            ?.let {
                Html.fromHtml(
                    it,
                    Html.FROM_HTML_MODE_LEGACY
                )
                    .toString()
                    .trim()
            }
            .orEmpty()

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    enabled =
                        !loading,

                    onClick =
                        onClick
                ),

        shape =
            RoundedCornerShape(
                16.dp
            )
    ) {
        Column(
            modifier =
                Modifier.padding(
                    16.dp
                )
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                if (
                    unread
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(
                                    8.dp
                                )
                                .background(
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .primary,

                                    shape =
                                        CircleShape
                                )
                    )

                    Spacer(
                        Modifier.size(
                            8.dp
                        )
                    )
                }

                Text(
                    text =
                        "$actorPrefix: $actor",

                    modifier =
                        Modifier.weight(1f),

                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,

                    fontWeight =
                        if (
                            unread
                        ) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Medium
                        }
                )

                val formattedDate =
                    formatMailboxDate(
                        dateRaw
                    )

                if (
                    formattedDate.isNotBlank()
                ) {
                    Spacer(
                        Modifier.size(
                            10.dp
                        )
                    )

                    Text(
                        text =
                            formattedDate,

                        style =
                            MaterialTheme
                                .typography
                                .labelMedium,

                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
                }
            }

            Spacer(
                Modifier.height(
                    7.dp
                )
            )

            Text(
                text =
                    topic.ifBlank {
                        "(bez tematu)"
                    },

                style =
                    MaterialTheme
                        .typography
                        .titleMedium,

                fontWeight =
                    if (
                        unread
                    ) {
                        FontWeight.Bold
                    } else {
                        FontWeight.SemiBold
                    }
            )

            if (
                hasAttachment
            ) {
                Spacer(
                    Modifier.height(
                        5.dp
                    )
                )

                Text(
                    text =
                        "Załącznik",

                    style =
                        MaterialTheme
                            .typography
                            .labelMedium,

                    color =
                        MaterialTheme
                            .colorScheme
                            .primary
                )
            }

            Spacer(
                Modifier.height(
                    10.dp
                )
            )

            /*
             * IMPORTANT:
             *
             * This is intentionally one if / else-if / else.
             * There is NO return from the Column composable lambda.
             */
            if (
                loading
            ) {
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically,

                    horizontalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        )
                ) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(
                                22.dp
                            ),

                        strokeWidth =
                            2.dp
                    )

                    Text(
                        text =
                            "Ładowanie treści…",

                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium
                    )
                }

            } else if (
                expanded
            ) {
                HorizontalDivider()

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                Text(
                    text =
                        plainBody.ifBlank {
                            "Brak treści wiadomości."
                        },

                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium
                )

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Zwiń",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (actions != null) {
                        actions()
                    }
                }

            } else {
                Text(
                    text =
                        "Pokaż treść",

                    style =
                        MaterialTheme
                            .typography
                            .labelLarge,

                    color =
                        MaterialTheme
                            .colorScheme
                            .primary,

                    fontWeight =
                        FontWeight.SemiBold
                )
            }
        }
    
        if (actions != null && !expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 12.dp
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions()
            }
        }
}
}

private val DISPLAY_DATE =
    DateTimeFormatter.ofPattern(
        "dd.MM.yyyy, HH:mm"
    )

private fun formatMailboxDate(
    raw: String?
): String {
    val value =
        raw
            ?.trim()
            .orEmpty()

    if (
        value.isBlank()
    ) {
        return ""
    }

    try {
        return LocalDateTime
            .parse(
                value,
                DateTimeFormatter
                    .ISO_LOCAL_DATE_TIME
            )
            .format(
                DISPLAY_DATE
            )

    } catch (
        ignored:
            DateTimeParseException
    ) {
        // Try offset format.
    }

    try {
        return OffsetDateTime
            .parse(
                value,
                DateTimeFormatter
                    .ISO_OFFSET_DATE_TIME
            )
            .toLocalDateTime()
            .format(
                DISPLAY_DATE
            )

    } catch (
        ignored:
            DateTimeParseException
    ) {
        // Fall back to server representation.
    }

    if (
        value.startsWith(
            "{"
        )
    ) {
        return ""
    }

    return value
}
