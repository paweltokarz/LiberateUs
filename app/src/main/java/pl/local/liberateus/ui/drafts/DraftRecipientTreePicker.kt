package pl.local.liberateus.ui.drafts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pl.local.liberateus.data.remote.dto.DraftRecipientGroup
import pl.local.liberateus.data.remote.dto.SelectableDraftRecipient

@Composable
internal fun DraftRecipientTreePicker(
    selected: SnapshotStateList<String>,
    initialNames: Map<String, String> = emptyMap(),
    enabled: Boolean,
    loadTypes: suspend () -> List<DraftRecipientGroup>,
    loadPeople: suspend (
        DraftRecipientGroup
    ) -> List<SelectableDraftRecipient>,
    onRecipientsDiscovered:
        (List<SelectableDraftRecipient>) -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    var groups by remember {
        mutableStateOf(emptyList<DraftRecipientGroup>())
    }
    var typesLoading by remember {
        mutableStateOf(true)
    }
    var typesError by remember {
        mutableStateOf<String?>(null)
    }

    val expanded = remember {
        mutableStateListOf<String>()
    }
    val loading = remember {
        mutableStateListOf<String>()
    }
    val cached = remember {
        mutableStateMapOf<
            String, List<SelectableDraftRecipient>
        >()
    }
    val errors = remember {
        mutableStateMapOf<String, String>()
    }

    LaunchedEffect(Unit) {
        typesLoading = true
        try {
            groups = loadTypes()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            typesError = failure.message
                ?.take(150)
                ?: "Nie udało się pobrać kategorii"
        } finally {
            typesLoading = false
        }
    }

    suspend fun fetch(
        group: DraftRecipientGroup
    ): List<SelectableDraftRecipient>? {
        cached[group.id]?.let { return it }

        if (group.id in loading) return null

        loading.add(group.id)
        errors.remove(group.id)

        return try {
            val result = loadPeople(group)
            onRecipientsDiscovered(result)
            cached[group.id] = result
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            errors[group.id] =
                failure.message?.take(150)
                    ?: "Nie udało się pobrać grupy"
            null
        } finally {
            loading.remove(group.id)
        }
    }

    Column(
        verticalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {
        /*
         * Combine names already present in an opened draft
         * with names discovered while browsing categories.
         */
        val knownNames = initialNames.toMutableMap()

        cached.values.forEach { people ->
            people.forEach { person ->
                if (person.name.isNotBlank()) {
                    knownNames[person.accountId] = person.name
                }
            }
        }

        val selectedNames = selectedRecipientNames(
            ids = selected.toList(),
            knownNames = knownNames
        )

        Text(
            text =
                "Odbiorcy (${selected.size})" +
                    if (selectedNames.isBlank()) {
                        ""
                    } else {
                        ", $selectedNames"
                    }
        )

        if (typesLoading) {
            CircularProgressIndicator()
            Text("Pobieranie kategorii…")
        } else if (typesError != null) {
            Text(
                "Błąd kategorii: " +
                    typesError.orEmpty()
            )
        } else if (groups.isEmpty()) {
            Text("Brak dostępnych kategorii odbiorców.")
        }

        groups.forEach { group ->
            val people = cached[group.id]
            val busy = group.id in loading
            val opened = group.id in expanded
            val allSelected =
                people?.isNotEmpty() == true &&
                    people.all {
                        it.accountId in selected
                    }

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (opened) {
                                expanded.remove(group.id)
                            } else {
                                expanded.add(group.id)
                                if (
                                    people == null &&
                                    !busy
                                ) {
                                    scope.launch {
                                        fetch(group)
                                    }
                                }
                            }
                        }
                    ) {
                        Text(
                            (if (opened) "▾ " else "▸ ") +
                                group.name,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Checkbox(
                        checked = allSelected,
                        enabled = enabled && !busy,
                        onCheckedChange = { checked ->
                            scope.launch {
                                val list =
                                    people ?: fetch(group)
                                        ?: return@launch
                                if (checked) {
                                    list.forEach { person ->
                                        if (
                                            person.accountId
                                            !in selected
                                        ) {
                                            selected.add(
                                                person.accountId
                                            )
                                        }
                                    }
                                } else {
                                    selected.removeAll(
                                        list.map {
                                            it.accountId
                                        }.toSet()
                                    )
                                }
                            }
                        }
                    )
                }

                if (opened) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(
                                start = 24.dp
                            )
                        )
                    } else if (
                        errors[group.id] != null
                    ) {
                        Text(
                            "Błąd: " +
                                errors[group.id].orEmpty(),
                            modifier = Modifier.padding(
                                start = 24.dp
                            )
                        )
                    } else {
                        val matching = people.orEmpty().take(100)

                        if (
                            people != null &&
                            people.isEmpty()
                        ) {
                            Text(
                                "Brak odbiorców",
                                modifier = Modifier.padding(
                                    start = 24.dp
                                )
                            )
                        }

                        var lastClass: String? = null

                        matching.forEach { person ->
                            if (
                                person.classLabel != null &&
                                person.classLabel != lastClass
                            ) {
                                Text(
                                    person.classLabel,
                                    modifier = Modifier.padding(
                                        start = 24.dp,
                                        top = 6.dp
                                    )
                                )
                                lastClass =
                                    person.classLabel
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp),
                                horizontalArrangement =
                                    Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked =
                                        person.accountId
                                            in selected,
                                    enabled = enabled,
                                    onCheckedChange = {
                                        checked ->
                                        if (checked) {
                                            if (
                                                person.accountId
                                                !in selected
                                            ) {
                                                selected.add(
                                                    person.accountId
                                                )
                                            }
                                        } else {
                                            selected.remove(
                                                person.accountId
                                            )
                                        }
                                    }
                                )
                                Text(
                                    person.name,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(top = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selected.isNotEmpty()) {
            TextButton(
                enabled = enabled,
                onClick = {
                    selected.clear()
                }
            ) {
                Text("Wyczyść zaznaczenie")
            }
        }
    }
}
