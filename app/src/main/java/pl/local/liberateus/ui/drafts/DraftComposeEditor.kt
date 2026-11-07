package pl.local.liberateus.ui.drafts
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pl.local.liberateus.data.remote.dto.DraftRecipientGroup
import pl.local.liberateus.data.remote.dto.SelectableDraftRecipient

private val ComposerBlue = Color(0xFF3998EA)
private val ComposerPale = Color(0xFFEAF4FF)
private val ComposerBorder = Color(0xFFCBD9E9)
private val ComposerInk = Color(0xFF15243D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DraftComposeEditor(
    initialTopic: String = "",
    initialContent: String = "",
    initialReceiversJson: String = "[]",
    initialRecipientNames: Map<String, String> = emptyMap(),
    editorTitle: String = "Nowa kopia robocza",
    saveAllowed: Boolean = true,
    onLoadRecipientTypes: suspend () ->
        List<DraftRecipientGroup>,
    onLoadRecipients: suspend (
        DraftRecipientGroup
    ) -> List<SelectableDraftRecipient>,
    onDismiss: () -> Unit,
    onSave: suspend (
        String,
        String,
        String
    ) -> Unit,
    onSend: (suspend (String, String, String) -> Unit)? = null,
    selfRecipientAccountId: String? = null
) {
    var topic by remember(initialTopic) {
        mutableStateOf(initialTopic)
    }

    var content by remember(initialContent) {
        mutableStateOf(initialContent)
    }

    val selected = remember(initialReceiversJson) {
        mutableStateListOf<String>().apply {
            addAll(
                editableSchoolRecipientIds(
                    initialReceiversJson
                ).orEmpty()
            )
        }
    }

    val names = remember(
        initialReceiversJson,
        initialRecipientNames
    ) {
        mutableStateMapOf<String, String>().apply {
            putAll(initialRecipientNames)
        }
    }

    var pickerOpen by remember {
        mutableStateOf(false)
    }

    var saving by remember {
        mutableStateOf(false)
    }

    var saveAttempted by remember {
        mutableStateOf(false)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    val scope = rememberCoroutineScope()

    var showSendConfirmation by remember { mutableStateOf(false) }
    var sendingMessage by remember { mutableStateOf(false) }
    var sendingAttempted by remember { mutableStateOf(false) }

    val canSendMessage =
        onSend != null &&
        !saving &&
        !sendingMessage &&
        !sendingAttempted &&
        topic.isNotBlank() &&
        content.isNotBlank() &&
        selected.isNotEmpty()

    if (showSendConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                if (!sendingMessage) showSendConfirmation = false
            },
            title = { Text("Potwierdź wysyłanie") },
            text = {
                Text(
                    "Wysłać wiadomość do ${selected.size} " +
                        "wybranych odbiorców?"
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val callback = onSend
                        if (callback != null && canSendMessage) {
                            sendingAttempted = true
                            sendingMessage = true
                            showSendConfirmation = false
                            val sendTopic = topic
                            val sendContent = content
                            val sendReceivers =
                                buildSchoolRecipientsJson(selected.toList())
                            scope.launch {
                                try {
                                    callback(
                                        sendTopic,
                                        sendContent,
                                        sendReceivers
                                    )
                                    onDismiss()
                                } catch (e: Exception) {
                                    if (
                                        e is kotlinx.coroutines.CancellationException
                                    ) throw e
                                    if (e is pl.local.liberateus.data
                                            .LibrusSendRejectedException) {
                                        errorMessage = e.message
                                        sendingAttempted = false
                                    } else {
                                        errorMessage =
                                            "Wynik wysyłania jest niepewny. " +
                                            "Sprawdź Wysłane przed ponowieniem. " +
                                            e.message.orEmpty().take(180)
                                    }
                                } finally {
                                    sendingMessage = false
                                }
                            }
                        }
                    }
                ) { Text("Wyślij") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showSendConfirmation = false }
                ) { Text("Anuluj") }
            }
        )
    }


    val saveEnabled =
        saveAllowed &&
            !saving &&
            !saveAttempted &&
            (
                topic.isNotBlank() ||
                    content.isNotBlank()
            )

    fun saveDraft() {
        if (!saveEnabled) return

        val savedTopic = topic
        val savedContent = content
        val savedRecipients =
            buildSchoolRecipientsJson(
                selected.toList()
            )

        saveAttempted = true
        saving = true
        errorMessage = null

        scope.launch {
            try {
                onSave(
                    savedTopic,
                    savedContent,
                    savedRecipients
                )
                onDismiss()
            } catch (
                cancelled: CancellationException
            ) {
                throw cancelled
            } catch (error: Exception) {
                errorMessage =
                    "Nie udało się potwierdzić zapisu. " +
                        "Sprawdź Robocze przed ponowieniem. " +
                        error.message
                            .orEmpty()
                            .take(150)
            } finally {
                saving = false
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (!saving) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = !saving
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                // Top composer toolbar.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 8.dp,
                            vertical = 6.dp
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = !saving,
                        onClick = onDismiss,
                        contentPadding =
                            PaddingValues(
                                horizontal = 8.dp
                            )
                    ) {
                        Text(
                            "‹",
                            fontSize = 30.sp,
                            color = ComposerBlue
                        )
                    }

                    Text(
                        "Liberate Us",
                        color = ComposerInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    TextButton(
                        onClick = ::saveDraft,
                        enabled = saveEnabled
                    ) {
                        Text("Zapisz")
                    }

                    OutlinedButton(
                        onClick = { showSendConfirmation = true },
                        enabled = canSendMessage,
                        contentPadding =
                            PaddingValues(
                                horizontal = 10.dp
                            )
                    ) {
                        if (sendingMessage) {
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment =
                                    androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement =
                                    androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = androidx.compose.ui.Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text("Wysyłanie / sprawdzanie…")
                            }
                        } else {
                            Text("Wyślij")
                        }
                    }
                }

                HorizontalDivider(
                    color = ComposerBorder
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(
                            rememberScrollState()
                        )
                        .padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        editorTitle,
                        style =
                            MaterialTheme.typography.titleMedium,
                        color = ComposerInk
                    )

                    if (!saveAllowed) {
                        Surface(
                            color = ComposerPale,
                            shape =
                                RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Ten szkic można przeglądać, " +
                                    "ale zapis pozostaje " +
                                    "zablokowany, ponieważ " +
                                    "nie wszystkie jego dane " +
                                    "są obsługiwane.",
                                modifier =
                                    Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Recipients: compact chips.
                    Surface(
                        color = Color.White,
                        shape =
                            RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            ComposerBorder
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth(),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Text(
                                    "Do (${selected.size})",
                                    fontWeight =
                                        FontWeight.Medium,
                                    color = ComposerInk
                                )

                                Spacer(
                                    modifier =
                                        Modifier.weight(1f)
                                )

                                TextButton(
                                    enabled =
                                        saveAllowed &&
                                            !saving &&
                                            !saveAttempted,
                                    onClick = {
                                        pickerOpen = true
                                    }
                                ) {
                                    Text(
                                        "Wybierz odbiorców"
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(
                                        rememberScrollState()
                                    ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(6.dp),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                if (selected.isEmpty()) {
                                    Text(
                                        "Brak odbiorców",
                                        color = Color.Gray
                                    )
                                }

                                selected.forEach { id ->
                                    Surface(
                                        color = ComposerPale,
                                        shape =
                                            RoundedCornerShape(
                                                50.dp
                                            )
                                    ) {
                                        Row(
                                            verticalAlignment =
                                                Alignment.CenterVertically,
                                            modifier =
                                                Modifier.padding(
                                                    start = 12.dp
                                                )
                                        ) {
                                            Text(
                                                names[id]
                                                    ?: "ID $id",
                                                color =
                                                    Color(
                                                        0xFF1760AA
                                                    ),
                                                maxLines = 1
                                            )

                                            TextButton(
                                                enabled =
                                                    saveAllowed &&
                                                        !saving &&
                                                        !saveAttempted,
                                                onClick = {
                                                    selected.remove(
                                                        id
                                                    )
                                                },
                                                contentPadding =
                                                    PaddingValues(
                                                        horizontal =
                                                            8.dp
                                                    )
                                            ) {
                                                Text("×")
                                            }
                                        }
                                    }
                                }

                                TextButton(
                                    enabled =
                                        saveAllowed &&
                                            !saving &&
                                            !saveAttempted,
                                    onClick = {
                                        pickerOpen = true
                                    }
                                ) {
                                    Text(
                                        "+",
                                        fontSize = 24.sp
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = topic,
                        onValueChange = {
                            topic = it
                            errorMessage = null
                        },
                        label = {
                            Text("Temat")
                        },
                        singleLine = true,
                        modifier =
                            Modifier.fillMaxWidth(),
                        enabled =
                            saveAllowed &&
                                !saving &&
                                !saveAttempted
                    )

                    OutlinedTextField(
                        value = content,
                        onValueChange = {
                            content = it
                            errorMessage = null
                        },
                        label = {
                            Text("Treść wiadomości")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min = 270.dp
                            ),
                        minLines = 10,
                        enabled =
                            saveAllowed &&
                                !saving &&
                                !saveAttempted
                    )

                    if (errorMessage != null) {
                        Text(
                            errorMessage.orEmpty(),
                            color =
                                MaterialTheme
                                    .colorScheme.error
                        )
                    }

                    if (
                        saveAttempted &&
                        errorMessage != null
                    ) {
                        Text(
                            "Zamknij edytor i sprawdź " +
                                "Robocze przed kolejną " +
                                "próbą zapisu.",
                            color = Color.Gray
                        )
                    }

                    Text(
                        "Przed wysłaniem sprawdź odbiorców. " +
                            "Po wysłaniu następuje weryfikacja w Wysłanych.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        if (pickerOpen) {
            val sheetState =
                rememberModalBottomSheetState(
                    skipPartiallyExpanded = true
                )

            ModalBottomSheet(
                onDismissRequest = {
                    pickerOpen = false
                },
                sheetState = sheetState,
                containerColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.88f)
                        .padding(
                            horizontal = 16.dp
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Text(
                            "Wybierz odbiorców",
                            style =
                                MaterialTheme
                                    .typography.titleLarge,
                            color = ComposerInk,
                            modifier =
                                Modifier.weight(1f)
                        )

                        TextButton(
                            onClick = {
                                pickerOpen = false
                            }
                        ) {
                            Text("Zamknij")
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(
                                rememberScrollState()
                            )
                    ) {
                        selfRecipientAccountId
                ?.takeIf { ENABLE_SELF_RECIPIENT_EXPERIMENT }
                ?.trim()
                ?.takeIf { id ->
                    id.isNotEmpty() && id.all { it.isDigit() }
                }
                ?.let { selfId ->
                    androidx.compose.material3.TextButton(
                        onClick = {
                            if (selfId !in selected) selected.add(selfId)
                            names[selfId] = "Ja (eksperymentalnie)"
                        }
                    ) {
                        Text("Dodaj siebie (test) · ID $selfId")
                    }
                }

                DraftRecipientTreePicker(
                            selected = selected,
                            initialNames =
                                names.toMap(),
                            enabled =
                                saveAllowed &&
                                    !saving &&
                                    !saveAttempted,
                            loadTypes =
                                onLoadRecipientTypes,
                            loadPeople =
                                onLoadRecipients,
                            onRecipientsDiscovered = {
                                people ->
                                people.forEach { person ->
                                    if (
                                        person.accountId
                                            .isNotBlank() &&
                                        person.name
                                            .isNotBlank()
                                    ) {
                                        names[
                                            person.accountId
                                        ] = person.name
                                    }
                                }
                            }
                        )
                    }

                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                bottom = 12.dp
                            ),
                        onClick = {
                            pickerOpen = false
                        }
                    ) {
                        Text(
                            "Gotowe (${selected.size})"
                        )
                    }
                }
            }
        }
    }
}

// Reserved for future self-addressing experiments.
private const val ENABLE_SELF_RECIPIENT_EXPERIMENT = false
