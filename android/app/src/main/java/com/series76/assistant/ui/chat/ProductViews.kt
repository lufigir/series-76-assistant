package com.series76.assistant.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.series76.assistant.data.remote.ActuatorData
import com.series76.assistant.data.remote.Requirements
import com.series76.assistant.data.remote.UnmetRequirement
import com.series76.assistant.ui.components.SectionLabel
import com.series76.assistant.ui.theme.PartNumberStyle
import com.series76.assistant.ui.theme.SpecValueStyle

private val LABEL_WIDTH = 124.dp

// Wide enough that another product runs off a phone's edge: the cut-off column is what says the table scrolls.
private val COLUMN_WIDTH = 156.dp

private val SPEC_TABLE_LABEL_WIDTH = 104.dp

/** A set of models from one answer. How many there are decides how they read best on a phone. */
@Composable
fun ProductResults(
    title: String,
    products: List<ActuatorData>,
    defaultUnit: TorqueDisplay,
    requirements: Requirements?,
    unmetByModel: List<List<UnmetRequirement>?>,
) {
    val checks = remember(requirements, unmetByModel) { requirementChecks(requirements, unmetByModel) }
    when (resultLayout(products.size)) {
        ResultLayout.CARD -> ProductCard(products.single(), defaultUnit, checks)
        ResultLayout.PAIR -> ComparisonTable(title, products, defaultUnit, checks)
        ResultLayout.LIST -> ProductList(title, products, defaultUnit, checks)
    }
}

private fun singleModel(checks: List<RequirementCheck>, index: Int) =
    checks.map { it.copy(results = listOf(it.results[index])) }

/** One actuator, read like a data sheet: identity, then its figures laid out by service and frequency. */
@Composable
private fun ProductCard(product: ActuatorData, defaultUnit: TorqueDisplay, checks: List<RequirementCheck>) {
    var unit by rememberSaveable { mutableStateOf(defaultUnit) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    DataPanel {
        Identity(product)
        if (product.notAvailable) {
            Notice("The data sheet lists this model as N/A, so it publishes no ratings for it.")
        } else {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (product.ratings.isNotEmpty()) {
                UnitSwitch(
                    unit,
                    onChange = { unit = it },
                    modifier = Modifier.align(Alignment.End).padding(horizontal = 6.dp),
                )
            }
            ModelDetails(product, unit, checks)
            ExpandRow(if (expanded) "Show less" else "More specifications", onToggle = { expanded = !expanded })
            if (expanded) {
                val scroll = rememberScrollState()
                remember(product, unit) { extraRows(product, unit) }.forEach { row ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ValueRow(row, listOf(product.partNumber), scroll, ColumnFit.CONTENT)
                }
            }
        }
    }
}

@Composable
private fun Identity(product: ActuatorData) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(product.partNumber, style = PartNumberStyle)
        Text(
            "${enclosureLabel(product.enclosure)} · ${powerSupplyLabel(product.powerSupply)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (product.notCsaCertified) {
            Text(
                "Not CSA certified",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/** What was asked first, then the data sheet: the first answers whether this model fits, the second what it is. */
@Composable
private fun ModelDetails(product: ActuatorData, unit: TorqueDisplay, checks: List<RequirementCheck>) {
    if (checks.isNotEmpty()) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
            SectionLabel("Your requirements", modifier = Modifier.padding(bottom = 4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            checks.forEach { check ->
                val result = check.results.single()
                Row(
                    modifier =
                        Modifier.fillMaxWidth().padding(vertical = 6.dp).clearAndSetSemantics {
                            contentDescription = "${check.label}: ${result.text}"
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        check.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        result.text,
                        style = SpecValueStyle,
                        color = checkColor(result.state),
                    )
                }
            }
        }
    }
    remember(product, unit) { specTables(product, unit) }.forEach { table -> SpecTableView(table) }
}

@Composable
private fun checkColor(state: CheckState): Color =
    when (state) {
        CheckState.UNMET -> MaterialTheme.colorScheme.tertiary
        CheckState.MET -> MaterialTheme.colorScheme.onSurface
        CheckState.NOT_CHECKED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
private fun SpecTableView(table: SpecTable) {
    Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Box(Modifier.width(SPEC_TABLE_LABEL_WIDTH))
            table.columns.forEach { column ->
                Text(
                    column,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        table.rows.forEach { row ->
            val spoken = row.label + ": " + table.columns.zip(row.values).joinToString(", ") { (c, v) -> "$c, $v" }
            // One announcement per row: TalkBack would otherwise read a bare figure with nothing saying what it measures.
            Row(
                modifier =
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).clearAndSetSemantics {
                        contentDescription = spoken
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(SPEC_TABLE_LABEL_WIDTH),
                )
                row.values.forEach { value ->
                    Text(value, style = SpecValueStyle, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Three or more models: one row each, opened in place, with a table one tap away for comparing them. */
@Composable
private fun ProductList(
    title: String,
    products: List<ActuatorData>,
    defaultUnit: TorqueDisplay,
    checks: List<RequirementCheck>,
) {
    var unit by rememberSaveable { mutableStateOf(defaultUnit) }
    var asTable by rememberSaveable { mutableStateOf(false) }

    AnimatedContent(
        targetState = asTable,
        transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
        label = "results",
    ) { table ->
        if (table) {
            Column {
                ComparisonTable(title, products, unit, checks)
                TextButton(onClick = { asTable = false }) { Text("Back to the list") }
            }
        } else {
            DataPanel {
                PanelHeader(title, unit, onChange = { unit = it })
                products.forEachIndexed { index, product ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ProductRow(product, unit, singleModel(checks, index))
                }
                ExpandRow("Compare in a table", onToggle = { asTable = true })
            }
        }
    }
}

@Composable
private fun ProductRow(product: ActuatorData, unit: TorqueDisplay, checks: List<RequirementCheck>) {
    var open by rememberSaveable(product.partNumber) { mutableStateOf(false) }
    val misses = checks.filter { it.results.single().state == CheckState.UNMET }

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(product.partNumber, style = PartNumberStyle.copy(fontSize = 15.sp))
            Text(summaryLine(product, unit), style = SpecValueStyle.copy(fontSize = 14.sp))
            if (misses.isNotEmpty()) {
                Text(
                    "Misses " + misses.joinToString { it.label },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (product.notCsaCertified) {
                Text(
                    "Not CSA certified",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        if (open && !product.notAvailable) ModelDetails(product, unit, checks)
    }
}

@Composable
private fun PanelHeader(title: String, unit: TorqueDisplay, onChange: (TorqueDisplay) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(title, modifier = Modifier.weight(1f))
        UnitSwitch(unit, onChange = onChange)
    }
}

/** Side by side: what was asked, then the rows that decide. The whole table opens full screen, where it has room. */
@Composable
private fun ComparisonTable(
    title: String,
    products: List<ActuatorData>,
    defaultUnit: TorqueDisplay,
    checks: List<RequirementCheck>,
) {
    var unit by rememberSaveable { mutableStateOf(defaultUnit) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val partNumbers = products.map { it.partNumber }
    val rows = remember(products, unit) { specRows(products, unit) }
    val fit = if (resultLayout(products.size) == ResultLayout.PAIR) ColumnFit.EQUAL else ColumnFit.SCROLL
    val scroll = rememberScrollState()

    DataPanel {
        PanelHeader(title, unit, onChange = { unit = it })
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ModelHeader(partNumbers, scroll, fit)
        checks.forEach { check ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CheckRow(check, partNumbers, scroll, fit)
        }
        remember(rows) { collapsedRows(rows) }.forEach { row ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ValueRow(row, partNumbers, scroll, fit)
        }
        ExpandRow("All ${rows.size} specifications", onToggle = { showAll = true })
    }
    if (showAll) ComparisonSheet(title, products, unit, checks, fit, onUnit = { unit = it }, onClose = { showAll = false })
}

/** The full table. A list of its own is what lets the part numbers stay pinned while the rows scroll under them. */
@Composable
private fun ComparisonSheet(
    title: String,
    products: List<ActuatorData>,
    unit: TorqueDisplay,
    checks: List<RequirementCheck>,
    fit: ColumnFit,
    onUnit: (TorqueDisplay) -> Unit,
    onClose: () -> Unit,
) {
    var onlyDifferences by rememberSaveable { mutableStateOf(false) }
    val partNumbers = products.map { it.partNumber }
    val sections = remember(products, unit, onlyDifferences) { specSections(products, unit, onlyDifferences) }
    val scroll = rememberScrollState()

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close the table") }
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    UnitSwitch(unit, onChange = onUnit, modifier = Modifier.padding(end = 6.dp))
                }
                FilterChip(
                    selected = onlyDifferences,
                    onClick = { onlyDifferences = !onlyDifferences },
                    label = { Text("Only differences") },
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    stickyHeader(key = "models", contentType = "models") {
                        Surface {
                            Column {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                ModelHeader(partNumbers, scroll, fit)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                    if (checks.isNotEmpty() && !onlyDifferences) {
                        item(key = "requirements-title", contentType = "section") { SheetSection("Your requirements") }
                        items(checks, key = { "check-${it.label}" }, contentType = { "row" }) { check ->
                            CheckRow(check, partNumbers, scroll, fit)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    sections.forEach { section ->
                        item(key = "section-${section.title}", contentType = "section") { SheetSection(section.title) }
                        items(section.rows, key = { it.label }, contentType = { "row" }) { row ->
                            ValueRow(row, partNumbers, scroll, fit)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    if (sections.isEmpty()) {
                        item(key = "same", contentType = "section") {
                            Text(
                                "These models have the same values in every row.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetSection(title: String) {
    SectionLabel(
        title,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 6.dp).semantics { heading() },
    )
}

@Composable
private fun ModelHeader(partNumbers: List<String>, scroll: ScrollState, fit: ColumnFit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RowLabel("Model")
        val shown = if (fit == ColumnFit.EQUAL) partNumbers.map(::partNumberOnTwoLines) else partNumbers
        Cells(shown, scroll, fit, PartNumberStyle.copy(fontSize = 13.sp, lineHeight = 17.sp))
    }
}

@Composable
private fun CheckRow(check: RequirementCheck, partNumbers: List<String>, scroll: ScrollState, fit: ColumnFit) {
    val spoken = check.label + ": " + partNumbers.zip(check.results).joinToString(", ") { (part, r) -> "$part, ${r.text}" }
    Row(modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }) {
        RowLabel(check.label)
        ColumnsRow(scroll, fit) { cellModifier ->
            check.results.forEach { result ->
                Text(result.text, style = SpecValueStyle, color = checkColor(result.state), modifier = cellModifier(this))
            }
        }
    }
}

@Composable
private fun DataPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                .animateContentSize(),
    ) {
        content()
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier =
            Modifier.fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                .background(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.shapes.small)
                .padding(10.dp),
    )
}

@Composable
private fun UnitSwitch(unit: TorqueDisplay, onChange: (TorqueDisplay) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
        TorqueDisplay.entries.forEach { option ->
            val selected = option == unit
            Text(
                option.label,
                style = MaterialTheme.typography.labelLarge,
                color =
                    if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier.minimumInteractiveComponentSize()
                        .clip(MaterialTheme.shapes.small)
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onChange(option) })
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ExpandRow(text: String, onToggle: () -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(14.dp),
    )
}

@Composable
private fun RowLabel(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.width(LABEL_WIDTH).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun ValueRow(row: SpecRow, partNumbers: List<String>, scroll: ScrollState, fit: ColumnFit) {
    val spoken =
        if (fit == ColumnFit.CONTENT) {
            row.label + ": " + row.values.first()
        } else {
            row.label + ": " + partNumbers.zip(row.values).joinToString(", ") { (part, value) -> "$part, $value" }
        }
    // One announcement per row: TalkBack would otherwise read a bare figure with nothing saying what it measures.
    Row(modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }) {
        RowLabel(row.label)
        Cells(row.values, scroll, fit, SpecValueStyle)
    }
}

/** EQUAL shares the width, for two models. SCROLL gives each a fixed column. CONTENT is a single value. */
private enum class ColumnFit { EQUAL, SCROLL, CONTENT }

@Composable
private fun Cells(values: List<String>, scroll: ScrollState, fit: ColumnFit, style: TextStyle) {
    ColumnsRow(scroll, fit) { cellModifier ->
        values.forEach { value -> Text(value, style = style, modifier = cellModifier(this)) }
    }
}

@Composable
private fun ColumnsRow(
    scroll: ScrollState,
    fit: ColumnFit,
    content: @Composable RowScope.(cellModifier: RowScope.() -> Modifier) -> Unit,
) {
    val padded = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    if (fit == ColumnFit.EQUAL) {
        Row(modifier = Modifier.fillMaxWidth()) { content { Modifier.weight(1f).then(padded) } }
    } else {
        // Every row shares one scroll state, so a swipe on any row moves all the columns together.
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.horizontalScroll(scroll)) {
                content { if (fit == ColumnFit.SCROLL) Modifier.width(COLUMN_WIDTH).then(padded) else padded }
            }
        }
    }
}
