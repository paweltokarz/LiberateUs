package pl.local.liberateus.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.local.liberateus.domain.model.InboxItem
import pl.local.liberateus.domain.model.Message
import pl.local.liberateus.domain.model.MessageContent

class MainUiViewModelTest {

    private fun inbox(
        id: String = "1",
        unread: Boolean = true
    ): InboxItem =
        InboxItem(
            id = id,
            sender = "Sender",
            topic = "Topic",
            sendDateRaw = "Date",
            sentAtEpochMillis = null,
            unread = unread,
            remoteOrder = 0,
            firstSeenAt = 1L,
            lastSeenAt = 1L
        )

    private fun fullMessage(
        id: String = "1",
        body: String = "Body"
    ): Message =
        Message(
            metadata =
                inbox(
                    id = id,
                    unread = false
                ),
            content =
                MessageContent(
                    messageId =
                        id,
                    content =
                        body,
                    fetchedAt =
                        2L
                )
        )

    @Test
    fun initialize_withoutLocalAccount_startsLoggedOut() {

        val vm =
            MainUiViewModel()

        vm.initialize(
            savedUsername = "",
            localAccountAvailable =
                false
        )

        assertEquals(
            "",
            vm.login.value
        )

        assertFalse(
            vm.connected.value
        )

        assertFalse(
            vm.onlineSession.value
        )

        assertEquals(
            "Niepołączono",
            vm.status.value
        )
    }

    @Test
    fun initialize_withLocalAccount_preservesImmediateOfflineAccess() {

        val vm =
            MainUiViewModel()

        vm.initialize(
            savedUsername =
                "student",
            localAccountAvailable =
                true
        )

        assertEquals(
            "student",
            vm.login.value
        )

        assertTrue(
            vm.connected.value
        )

        assertFalse(
            vm.onlineSession.value
        )

        assertEquals(
            "Tryb lokalny",
            vm.status.value
        )
    }

    @Test
    fun initialize_runsOnlyOnce() {

        val vm =
            MainUiViewModel()

        vm.initialize(
            savedUsername =
                "first",
            localAccountAvailable =
                true
        )

        vm.login.value =
            "edited"

        vm.status.value =
            "Połączono"

        vm.initialize(
            savedUsername =
                "second",
            localAccountAvailable =
                false
        )

        assertEquals(
            "edited",
            vm.login.value
        )

        assertEquals(
            "Połączono",
            vm.status.value
        )

        assertTrue(
            vm.connected.value
        )
    }

    @Test
    fun presentationValues_areMutableThroughViewModel() {

        val vm =
            MainUiViewModel()

        vm.messages.value =
            listOf(
                inbox()
            )

        vm.expandedMessage.value =
            fullMessage()

        vm.directMessage.value =
            fullMessage(
                id = "2",
                body = "Direct"
            )

        vm.openingMessageId.value =
            "1"

        assertEquals(
            1,
            vm.messages.value.size
        )

        assertEquals(
            "Body",
            vm.expandedMessage
                .value
                ?.content
                ?.content
        )

        assertEquals(
            "Direct",
            vm.directMessage
                .value
                ?.content
                ?.content
        )

        assertEquals(
            "1",
            vm.openingMessageId.value
        )
    }

    @Test
    fun resetForLogout_clearsPresentationState() {

        val vm =
            MainUiViewModel()

        vm.initialize(
            savedUsername =
                "student",
            localAccountAvailable =
                true
        )

        vm.password.value =
            "password"

        vm.onlineSession.value =
            true

        vm.busy.value =
            true

        vm.messages.value =
            listOf(
                inbox()
            )

        vm.expandedMessage.value =
            fullMessage()

        vm.directMessage.value =
            fullMessage(
                id = "2"
            )

        vm.directMessageLoading.value =
            true

        vm.directMessageError.value =
            "error"

        vm.openingMessageId.value =
            "1"

        vm.resetForLogout()

        assertEquals(
            "",
            vm.login.value
        )

        assertEquals(
            "",
            vm.password.value
        )

        assertFalse(
            vm.connected.value
        )

        assertFalse(
            vm.onlineSession.value
        )

        assertFalse(
            vm.busy.value
        )

        assertEquals(
            "Wylogowano",
            vm.status.value
        )

        assertTrue(
            vm.messages.value
                .isEmpty()
        )

        assertNull(
            vm.expandedMessage.value
        )

        assertNull(
            vm.directMessage.value
        )

        assertFalse(
            vm.directMessageLoading.value
        )

        assertNull(
            vm.directMessageError.value
        )

        assertNull(
            vm.openingMessageId.value
        )
    }


    @Test
    fun resetForLogout_clearsTransientPresentationState() {

        val viewModel =
            MainUiViewModel()

        viewModel.login.value =
            "user-A"

        viewModel.password.value =
            "secret"

        viewModel.connected.value =
            true

        viewModel.onlineSession.value =
            true

        viewModel.busy.value =
            true

        viewModel.status.value =
            "Otwieranie wiadomości…"

        viewModel.directMessageLoading.value =
            true

        viewModel.directMessageError.value =
            "stale error"

        viewModel.openingMessageId.value =
            "123"

        viewModel.resetForLogout()

        assertEquals(
            "",
            viewModel.login.value
        )

        assertEquals(
            "",
            viewModel.password.value
        )

        assertFalse(
            viewModel.connected.value
        )

        assertFalse(
            viewModel.onlineSession.value
        )

        assertFalse(
            viewModel.busy.value
        )

        assertEquals(
            "Wylogowano",
            viewModel.status.value
        )

        assertTrue(
            viewModel.messages.value
                .isEmpty()
        )

        assertNull(
            viewModel.expandedMessage.value
        )

        assertNull(
            viewModel.directMessage.value
        )

        assertFalse(
            viewModel.directMessageLoading.value
        )

        assertNull(
            viewModel.directMessageError.value
        )

        assertNull(
            viewModel.openingMessageId.value
        )
    }

}
