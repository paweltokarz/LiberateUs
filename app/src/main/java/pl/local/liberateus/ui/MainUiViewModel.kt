package pl.local.liberateus.ui

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import pl.local.liberateus.domain.model.InboxItem
import pl.local.liberateus.domain.model.Message
import pl.local.liberateus.domain.model.Draft
import pl.local.liberateus.ui.mailbox.MailboxTab
import pl.local.liberateus.domain.model.SentMessage

/**
 * Presentation state for the main application flow.
 *
 * Transport DTOs do not cross into this layer.
 */
class MainUiViewModel : ViewModel() {

    private var initialized =
        false

    val login =
        mutableStateOf("")

    val password =
        mutableStateOf("")

    val connected =
        mutableStateOf(false)

    val onlineSession =
        mutableStateOf(false)

    val busy =
        mutableStateOf(false)

    val status =
        mutableStateOf(
            "Niepołączono"
        )


    val selectedMailbox =
        mutableStateOf(
            MailboxTab.INBOX
        )

    val drafts =
        mutableStateOf<
            List<Draft>
        >(
            emptyList()
        )

    val expandedDraftId =
        mutableStateOf<
            String?
        >(
            null
        )


    val sentMessages =
        mutableStateOf<
            List<SentMessage>
        >(
            emptyList()
        )

    val expandedSentMessageId =
        mutableStateOf<
            String?
        >(
            null
        )

    val messages =
        mutableStateOf<
            List<InboxItem>
        >(
            emptyList()
        )

    val expandedMessage =
        mutableStateOf<
            Message?
        >(
            null
        )

    val directMessage =
        mutableStateOf<
            Message?
        >(
            null
        )

    val directMessageLoading =
        mutableStateOf(false)

    val directMessageError =
        mutableStateOf<String?>(
            null
        )

    val openingMessageId =
        mutableStateOf<String?>(
            null
        )

    fun initialize(
        savedUsername: String,
        localAccountAvailable: Boolean
    ) {

        if (initialized) {
            return
        }

        initialized =
            true

        login.value =
            savedUsername

        connected.value =
            localAccountAvailable

        onlineSession.value =
            false

        busy.value =
            false

        status.value =
            if (
                localAccountAvailable
            ) {
                "Tryb lokalny"
            } else {
                "Niepołączono"
            }
    }

    fun resetForLogout() {

        login.value =
            ""

        password.value =
            ""

        connected.value =
            false

        onlineSession.value =
            false

        busy.value =
            false

        status.value =
            "Wylogowano"

        messages.value =
            emptyList()

        expandedMessage.value =
            null

        directMessage.value =
            null

        directMessageLoading.value =
            false

        directMessageError.value =
            null

        openingMessageId.value =
            null


        selectedMailbox.value =
            MailboxTab.INBOX

        drafts.value =
            emptyList()

        sentMessages.value =
            emptyList()

        expandedDraftId.value =
            null

        expandedSentMessageId.value =
            null
    }
}
