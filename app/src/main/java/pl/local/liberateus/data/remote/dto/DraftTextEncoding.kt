package pl.local.liberateus.data.remote.dto

import android.util.Base64

/**
 * Librus frontend's fr(value) uses Base64 encoding.
 * UTF-8 conversion is required before Base64.
 *
 * Base64.NO_WRAP preserves standard padding and avoids
 * inserting line breaks into JSON fields.
 */
internal fun encodeDraftText(
    value: String
): String =
    encodeDraftTextWith(value) { bytes ->
        Base64.encodeToString(
            bytes,
            Base64.NO_WRAP
        )
    }

/**
 * Encoder injection keeps UTF-8 conversion testable
 * in ordinary JVM unit tests.
 */
internal fun encodeDraftTextWith(
    value: String,
    encoder: (ByteArray) -> String
): String =
    encoder(
        value.toByteArray(Charsets.UTF_8)
    )
