package io.androllm.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import io.androllm.core.memory.MemoryCategory
import io.androllm.core.memory.MemoryStatus
import io.androllm.core.memory.model.Memory
import io.androllm.core.ui.components.CloudAtmosphericBackground
import io.androllm.core.ui.components.CloudChip
import io.androllm.core.ui.components.CloudGlassCard
import io.androllm.core.ui.theme.ledger

/** Bosnian display name of a memory category. */
internal fun MemoryCategory.bosnianLabel(): String = when (this) {
    MemoryCategory.IDENTITY -> "Identitet"
    MemoryCategory.PREFERENCES -> "Preference"
    MemoryCategory.PROJECTS -> "Projekti"
    MemoryCategory.GOALS -> "Ciljevi"
    MemoryCategory.SKILLS -> "Vještine"
    MemoryCategory.PROGRAMMING_LANGUAGES -> "Programski jezici"
    MemoryCategory.FRAMEWORKS -> "Radni okviri"
    MemoryCategory.DEVICES -> "Uređaji"
    MemoryCategory.PINNED_FACTS -> "Prikvačene činjenice"
    MemoryCategory.DEVELOPER_NOTES -> "Bilješke za razvoj"
    MemoryCategory.CUSTOM -> "Ostalo"
    MemoryCategory.RELATIONSHIP_MOMENT -> "Šale i lijepi trenuci"
    MemoryCategory.RELATIONSHIP_CONFLICT -> "Svađe"
    MemoryCategory.RELATIONSHIP_DISLIKE -> "Što joj smeta"
}

private val RELATIONSHIP_ORDER = listOf(
    MemoryCategory.RELATIONSHIP_MOMENT,
    MemoryCategory.RELATIONSHIP_CONFLICT,
    MemoryCategory.RELATIONSHIP_DISLIKE
)

/**
 * "Memorija": review screen for Alexa's memories. Suggestions (extracted
 * automatically) are listed first with Potvrdi / Odbaci; only confirmed
 * memories are ever used in the model prompt. Confirmed memories can be
 * corrected (the old claim is replaced) or deleted after a confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryReviewScreen(
    navController: NavController,
    viewModel: MemoryReviewViewModel = hiltViewModel()
) {
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var categoryFilter by remember { mutableStateOf<MemoryCategory?>(null) }
    var editing by remember { mutableStateOf<Memory?>(null) }
    var deleting by remember { mutableStateOf<Memory?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val text = message
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

    val filtered = memories.filter { categoryFilter == null || it.category == categoryFilter }
    val suggestions = filtered.filter { it.status == MemoryStatus.SUGGESTED }
    val confirmed = filtered.filter { it.status == MemoryStatus.CONFIRMED }
    val general = confirmed.filter { !it.category.isRelationship }
    val relationship = confirmed.filter { it.category.isRelationship }

    CloudAtmosphericBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = "Nazad",
                                tint = MaterialTheme.ledger.deskInk
                            )
                        }
                    },
                    title = {
                        Text(
                            text = "Memorija",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.ledger.deskPaper
                            )
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "filters") {
                    CategoryFilterRow(
                        selected = categoryFilter,
                        onSelected = { categoryFilter = it }
                    )
                }

                if (suggestions.isNotEmpty()) {
                    item(key = "header-suggestions") {
                        SectionHeader(
                            title = "Prijedlozi (${suggestions.size})",
                            hint = "Alexa ove prijedloge ne koristi dok ih ne potvrdite."
                        )
                    }
                    items(suggestions, key = { it.id }) { memory ->
                        MemoryCard(
                            memory = memory,
                            isSuggestion = true,
                            onConfirm = { viewModel.confirm(memory.id) },
                            onReject = { viewModel.reject(memory.id) },
                            onEdit = { editing = memory },
                            onDelete = {}
                        )
                    }
                }

                item(key = "header-confirmed") {
                    SectionHeader(
                        title = "Potvrđena sjećanja (${general.size})",
                        hint = null
                    )
                }
                if (general.isEmpty()) {
                    item(key = "empty-confirmed") {
                        EmptyHint("Nema potvrđenih sjećanja za ovaj prikaz.")
                    }
                } else {
                    items(general, key = { it.id }) { memory ->
                        MemoryCard(
                            memory = memory,
                            isSuggestion = false,
                            onConfirm = {},
                            onReject = {},
                            onEdit = { editing = memory },
                            onDelete = { deleting = memory }
                        )
                    }
                }

                item(key = "header-relationship") {
                    SectionHeader(
                        title = "Odnos s Alexom (${relationship.size})",
                        hint = "Šale, svađe i ono što joj smeta."
                    )
                }
                if (relationship.isEmpty()) {
                    item(key = "empty-relationship") {
                        EmptyHint("Još nema sjećanja o odnosu.")
                    }
                } else {
                    for (category in RELATIONSHIP_ORDER) {
                        val inCategory = relationship.filter { it.category == category }
                        if (inCategory.isEmpty()) continue
                        item(key = "sub-${category.name}") {
                            Text(
                                text = category.bosnianLabel(),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.ledger.lampDeep
                                ),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        items(inCategory, key = { it.id }) { memory ->
                            MemoryCard(
                                memory = memory,
                                isSuggestion = false,
                                onConfirm = {},
                                onReject = {},
                                onEdit = { editing = memory },
                                onDelete = { deleting = memory }
                            )
                        }
                    }
                }

                item(key = "bottom-space") { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    editing?.let { target ->
        val isSuggestion = target.status == MemoryStatus.SUGGESTED
        EditMemoryDialog(
            initial = target.content,
            title = if (isSuggestion) "Izmijeni prijedlog" else "Ispravi sjećanje",
            hint = if (isSuggestion) {
                "Tekst prijedloga možete urediti prije potvrde."
            } else {
                "Stara tvrdnja se zamjenjuje ispravkom i Alexa je više neće koristiti."
            },
            confirmLabel = if (isSuggestion) "Sačuvaj" else "Sačuvaj ispravku",
            onDismiss = { editing = null },
            onSave = { text ->
                if (isSuggestion) viewModel.editSuggestion(target.id, text) else viewModel.correct(target.id, text)
                editing = null
            }
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = {
                Text(
                    text = "Obrisati sjećanje?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.ledger.deskPaper
                )
            },
            text = {
                Text(
                    text = "\"${target.content}\"\n\nOvo se ne može poništiti.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.ledger.deskInk)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.delete(target.id)
                        deleting = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Obriši")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text("Odustani", color = MaterialTheme.ledger.deskInk)
                }
            }
        )
    }
}

@Composable
private fun CategoryFilterRow(
    selected: MemoryCategory?,
    onSelected: (MemoryCategory?) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "all") {
            CloudChip(
                text = "Sve",
                modifier = Modifier.clickable { onSelected(null) },
                accentColor = if (selected == null) MaterialTheme.ledger.lampAmber else MaterialTheme.ledger.deskInk
            )
        }
        items(MemoryCategory.entries.toList(), key = { it.name }) { category ->
            CloudChip(
                text = category.bosnianLabel(),
                modifier = Modifier.clickable { onSelected(category) },
                accentColor = if (selected == category) MaterialTheme.ledger.lampAmber else MaterialTheme.ledger.deskInk
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, hint: String?) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.ledger.deskPaper
            )
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.ledger.deskInk)
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.ledger.deskInk),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun MemoryCard(
    memory: Memory,
    isSuggestion: Boolean,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    CloudGlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.ledger.deskPaper)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CloudChip(
                    text = memory.category.bosnianLabel(),
                    accentColor = MaterialTheme.ledger.lampDeep
                )
                Spacer(modifier = Modifier.weight(1f))
                if (isSuggestion) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Uredi",
                            tint = MaterialTheme.ledger.deskInk
                        )
                    }
                } else {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Ispravi",
                            tint = MaterialTheme.ledger.deskInk
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Obriši",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            if (isSuggestion) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.ledger.lampAmber,
                            contentColor = MaterialTheme.ledger.inkOnLamp
                        )
                    ) {
                        Text("Potvrdi")
                    }
                    OutlinedButton(onClick = onReject) {
                        Text("Odbaci", color = MaterialTheme.ledger.deskPaper)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditMemoryDialog(
    initial: String,
    title: String,
    hint: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.ledger.deskPaper) },
        text = {
            Column {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.ledger.deskInk)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Sjećanje") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(text.trim()) },
                enabled = text.isNotBlank() && text.trim() != initial.trim(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.ledger.lampAmber,
                    contentColor = MaterialTheme.ledger.inkOnLamp
                )
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Odustani", color = MaterialTheme.ledger.deskInk) }
        }
    )
}
