package pl.local.liberateus.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.local.liberateus.ui.mailbox.MailboxTab

class MailboxUiStateTest {

    @Test
    fun `mailbox starts on inbox`() {
        val viewModel =
            MainUiViewModel()

        assertEquals(
            MailboxTab.INBOX,
            viewModel
                .selectedMailbox
                .value
        )
    }

    @Test
    fun `draft state starts empty`() {
        val viewModel =
            MainUiViewModel()

        assertTrue(
            viewModel
                .drafts
                .value
                .isEmpty()
        )

        assertNull(
            viewModel
                .expandedDraftId
                .value
        )
    }

    @Test
    fun `sent state starts empty`() {
        val viewModel =
            MainUiViewModel()

        assertTrue(
            viewModel
                .sentMessages
                .value
                .isEmpty()
        )

        assertNull(
            viewModel
                .expandedSentMessageId
                .value
        )
    }

    @Test
    fun `mailbox enum contains three mailboxes`() {
        assertEquals(
            listOf(
                MailboxTab.INBOX,
                MailboxTab.SENT,
                MailboxTab.DRAFTS
            ),
            MailboxTab.entries
        )
    }


    @Test
    fun `logout resets all mailbox presentation state`() {
        val viewModel =
            MainUiViewModel()

        viewModel
            .selectedMailbox
            .value =
            MailboxTab.SENT

        viewModel
            .expandedDraftId
            .value =
            "draft"

        viewModel
            .expandedSentMessageId
            .value =
            "sent"

        viewModel
            .resetForLogout()

        assertEquals(
            MailboxTab.INBOX,
            viewModel
                .selectedMailbox
                .value
        )

        assertTrue(
            viewModel
                .drafts
                .value
                .isEmpty()
        )

        assertTrue(
            viewModel
                .sentMessages
                .value
                .isEmpty()
        )

        assertNull(
            viewModel
                .expandedDraftId
                .value
        )

        assertNull(
            viewModel
                .expandedSentMessageId
                .value
        )
    }

}
