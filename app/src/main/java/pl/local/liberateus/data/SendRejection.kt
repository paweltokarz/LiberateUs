package pl.local.liberateus.data

import java.io.IOException
import org.json.JSONObject

internal fun isDefinitiveSendRejection(code: Int): Boolean =
    code in 400..499 && code !in setOf(408, 425, 429)

internal fun readableSendRejection(body: String?): String? {
    if (body.isNullOrBlank()) return null
    val json = runCatching { JSONObject(body.take(16000)) }
        .getOrNull() ?: return null
    for (key in listOf(
        "message", "detail", "description",
        "error_description", "errors", "error"
    )) {
        val value = json.opt(key) ?: continue
        val text = when (value) {
            is JSONObject -> value.optString("message")
                .ifBlank { value.toString() }
            else -> value.toString()
        }.replace(Regex("\\s+"), " ").trim()
        if (text.isNotBlank() &&
            !text.equals("null", true) &&
            !text.startsWith("<")) return text.take(240)
    }
    return null
}

internal class LibrusSendRejectedException(
    val status: Int,
    reason: String?
) : IOException(
    "Serwer Librusa odrzucił wiadomość (HTTP $status). " +
        (reason ?: "Odbiorca lub dane wiadomości nie zostały zaakceptowane.") +
        " Wiadomość nie została przyjęta przez serwer."
)
