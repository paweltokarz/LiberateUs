package pl.local.liberateus.ui.components

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val messageDateFormatter =
    DateTimeFormatter.ofPattern(
        "dd.MM.yyyy, HH:mm",
        Locale.forLanguageTag(
            "pl-PL"
        )
    )

internal fun formatMessageDate(
    raw: String
): String {

    val value =
        raw.trim()

    if (
        value.isBlank()
    ) {
        return ""
    }

    val parsed =
        runCatching {
            OffsetDateTime
                .parse(
                    value
                )
                .toLocalDateTime()
        }
            .getOrNull()
            ?: runCatching {
                LocalDateTime.parse(
                    value
                )
            }
                .getOrNull()

    if (
        parsed != null
    ) {
        return parsed.format(
            messageDateFormatter
        )
    }

    /*
     * Graceful fallback for an unexpected server format.
     *
     * Never break presentation just because Librus changed
     * the date representation.
     */
    return value
        .replace(
            'T',
            ' '
        )
}
