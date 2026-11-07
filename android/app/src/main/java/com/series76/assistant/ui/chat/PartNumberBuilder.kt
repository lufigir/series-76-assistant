package com.series76.assistant.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.series76.assistant.data.remote.CatalogEntry
import com.series76.assistant.ui.theme.PartNumberStyle

/** Part numbers the end of the draft could become. One tap puts the whole number in place of what was typed. */
@Composable
internal fun CompletionRow(completions: List<String>, onPick: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        completions.forEach { partNumber ->
            SuggestionChip(
                onClick = { onPick(partNumber) },
                label = { Text(partNumber, style = PartNumberStyle.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize)) },
            )
        }
    }
}

/** Size, then enclosure, then supply, each offering only what exists, ending in the part numbers that remain. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PartNumberBuilderSheet(catalog: List<CatalogEntry>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var size by rememberSaveable { mutableStateOf<String?>(null) }
    var enclosure by rememberSaveable { mutableStateOf<String?>(null) }
    var powerSupply by rememberSaveable { mutableStateOf<String?>(null) }
    val choice = PartNumberChoice(size, enclosure, powerSupply)
    val enclosures = if (size == null) emptyList() else enclosureOptions(catalog, choice)
    val supplies = if (enclosure == null) emptyList() else powerSupplyOptions(catalog, choice)
    // A step with a single answer is no choice, so it fills itself in.
    LaunchedEffect(enclosures) { if (enclosure == null) enclosures.singleOrNull()?.let { enclosure = it } }
    LaunchedEffect(supplies) { if (powerSupply == null) supplies.singleOrNull()?.let { powerSupply = it } }
    val scroll = rememberScrollState()
    // Each answer folds its step and opens the next below it, so the sheet follows to keep that step in view.
    LaunchedEffect(choice) { scroll.animateScrollTo(scroll.maxValue) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Build a part number", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(
                    "Only combinations in the data tables are offered.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Preview(choice)
            Step(1, "Size", sizeOptions(catalog), size, label = { it }) {
                size = it
                enclosure = null
                powerSupply = null
            }
            if (size != null) {
                Step(2, "Enclosure", enclosures, enclosure, label = ::enclosureLabel) {
                    enclosure = it
                    powerSupply = null
                }
            }
            if (enclosure != null) {
                Step(3, "Power supply", supplies, powerSupply, label = ::powerSupplyLabel) { powerSupply = it }
            }
            if (powerSupply != null) {
                val built = builtPartNumbers(catalog, choice)
                StepTitle(4, if (built.size == 1) "Part number" else "Pick a model")
                built.forEach { partNumber ->
                    Surface(
                        onClick = { onPick(partNumber) },
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                partNumber,
                                style = PartNumberStyle,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "Add",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** What has been chosen so far, read as one line, so the reader sees the number take shape. */
@Composable
private fun Preview(choice: PartNumberChoice) {
    val parts =
        listOfNotNull(
            choice.size,
            choice.enclosure?.let(::enclosureLabel),
            choice.powerSupply?.let(::powerSupplyLabel),
        )
    Text(
        if (parts.isEmpty()) "Start with the size" else parts.joinToString("  ·  "),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier =
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium)
                .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun StepTitle(number: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "$number",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.size(22.dp).background(MaterialTheme.colorScheme.primary, CircleShape).wrapContentHeight(),
        )
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step(
    number: Int,
    title: String,
    options: List<String>,
    selected: String?,
    label: (String) -> String,
    onSelect: (String?) -> Unit,
) {
    // A step already answered folds to one line, so the open step and the result stay on screen.
    if (selected != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f)) { StepTitle(number, "$title: ${label(selected)}") }
            TextButton(onClick = { onSelect(null) }) { Text("Change") }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StepTitle(number, title)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { option ->
                FilterChip(selected = false, onClick = { onSelect(option) }, label = { Text(label(option)) })
            }
        }
    }
}
