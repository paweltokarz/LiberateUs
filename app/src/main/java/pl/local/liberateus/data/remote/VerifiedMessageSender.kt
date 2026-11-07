package pl.local.liberateus.data.remote


import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

private fun normalizeConfirmationText(value: String): String =
    value.replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace('\u00a0', ' ')
        .trim()

internal fun matchesNewSentMessage(
    previousIds: Set<String>,
    id: String,
    sentTopic: String,
    sentBody: String,
    receiverId: String?,
    expectedTopic: String,
    expectedBody: String,
    recipientIds: Set<String>
): Boolean {
    val actualTopic = normalizeConfirmationText(sentTopic)
    val expectedTopicNormalized = normalizeConfirmationText(expectedTopic)
    val actualBody = normalizeConfirmationText(sentBody)
    val expectedBodyNormalized = normalizeConfirmationText(expectedBody)
    val bodyMatches =
        actualBody == expectedBodyNormalized ||
        (
            actualBody.length >= 40 &&
            expectedBodyNormalized.startsWith(actualBody)
        )
    return id.isNotBlank() &&
        id !in previousIds &&
        recipientIds.isNotEmpty() &&
        actualTopic == expectedTopicNormalized &&
        bodyMatches &&
        (receiverId.isNullOrBlank() || receiverId in recipientIds)
}

/**
 * The POST is not idempotent: never retry it.
 * A failed confirmation does not mean the server rejected the message.
 */
internal class VerifiedMessageSender(connection: LibrusConnection) {
    private val client = connection.legacyClient

    suspend fun send(
        topic: String,
        content: String,
        receiversJson: String
    ) {
        require(topic.isNotBlank() && content.isNotBlank())

        val receivers = JSONObject(receiversJson)
            .getJSONArray("schoolReceivers")
        val recipientIds = (0 until receivers.length())
            .map {
                receivers.getJSONObject(it)
                    .getString("accountId").trim()
            }.toSet()

        require(recipientIds.isNotEmpty())
        require(recipientIds.none { it.isBlank() })

        // A failed baseline GET must prevent the POST.
        val baseline = withContext(Dispatchers.IO) {
            client.fetchSentMessages(100)
        }
        val previousIds = baseline.messages.map { it.id }.toSet()

        // Exactly one POST; no automatic retry.
        withContext(Dispatchers.IO) {
            client.sendMessageOnce(topic, content, receiversJson)
        }

        var confirmed = false
        withTimeoutOrNull(10_000L) {
            while (!confirmed) {
                delay(1_000L)
                val page = try {
                    withContext(Dispatchers.IO) {
                        client.fetchSentMessages(100)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                confirmed = page?.messages?.any { message ->
                    matchesNewSentMessage(
                        previousIds,
                        message.id,
                        message.topic,
                        message.body,
                        message.receiverAccountId,
                        topic,
                        content,
                        recipientIds
                    )
                } == true
            }
        }
        if (!confirmed) {
            error(
                "Nie potwierdzono wysłania w folderze Wysłane. " +
                "Sprawdź folder przed ponowieniem."
            )
        }
    }
}
