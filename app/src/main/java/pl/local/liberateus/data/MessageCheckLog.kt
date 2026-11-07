package pl.local.liberateus.data

import android.content.Context
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class MessageFetchSource {
    MANUAL,
    PERIODIC,
    ONE_SHOT_TEST,
    UNKNOWN
}

class MessageCheckLog(
    context: Context
) {
    companion object {
        private const val FILE_NAME =
            "message_checks.log"

        private const val OLD_FILE_NAME =
            "message_checks.log.1"

        private const val MAX_BYTES =
            1024L * 1024L
    }

    private val filesDir =
        context.applicationContext.filesDir

    private val file =
        File(filesDir, FILE_NAME)

    private val oldFile =
        File(filesDir, OLD_FILE_NAME)

    private val zone =
        ZoneId.of("Europe/Warsaw")

    @Synchronized
    fun write(
        source: MessageFetchSource,
        event: String,
        details: String = ""
    ) {
        rotateIfNeeded()

        val timestamp =
            ZonedDateTime.now(zone)
                .format(
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME
                )

        val safeDetails =
            details
                .replace('\n', ' ')
                .replace('\r', ' ')
                .take(1000)

        val line =
            buildString {
                append(timestamp)
                append(" source=")
                append(source.name)
                append(" event=")
                append(event)

                if (safeDetails.isNotBlank()) {
                    append(" ")
                    append(safeDetails)
                }

                append('\n')
            }

        runCatching {
            file.appendText(line)
        }
    }

    private fun rotateIfNeeded() {
        if (
            file.exists() &&
            file.length() >= MAX_BYTES
        ) {
            runCatching {
                if (oldFile.exists()) {
                    oldFile.delete()
                }

                file.renameTo(oldFile)
            }
        }
    }
}
