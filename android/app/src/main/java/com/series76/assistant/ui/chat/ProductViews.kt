package com.series76.assistant.ui.chat

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.series76.assistant.data.remote.ActuatorData
import com.series76.assistant.ui.components.SectionLabel
import com.series76.assistant.ui.theme.PartNumberStyle
import com.series76.assistant.ui.theme.SpecValueStyle

private val LABEL_WIDTH = 124.dp

// Wide enough that a second product runs off a phone's edge: the cut-off column is what says the table scrolls.
private val COLUMN_WIDTH = 156.dp

private val SpecTableLabelWidth = 104.dp

/** One actuator, read like a data sheet: identity, then its figures laid out by service and frequency. */
@Composable
fun ProductCard(product: ActuatorData, defaultUnit: TorqueDisplay) {
    var unit by rememberSaveable { mutableStateOf(defaultUnit) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val tables = remember(product, unit) { specTables(product, unit) }
    val extras = remember(product, unit) { extraRows(product, unit) }

    DataPanel {
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
            tables.forEach { table -> SpecTableView(table) }
            ExpandRow(if (expanded) "Show less" else "More specifications", onToggle = { expanded = !expanded })
            if (expanded) {
                extras.forEach { row ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ValueRow(row, listOf(product.partNumber), rememberScrollState(), comparing = false)
                }
            }
        }
    }
}

@Composable
private fun SpecTableView(table: SpecTable) {
    Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Box(Modifier.width(SpecTableLabelWidth))
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
                    modifier = Modifier.width(SpecTableLabelWidth),
                )
                row.values.forEach { value ->
                    Text(value, style = SpecValueStyle, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Two or more actuators: labels hold still while values scroll sideways, and rows narrow to what differs. */
@Composable
fun ComparisonTable(
    title: String,
    products: List<ActuatorData>,
    defaultUnit: TorqueDisplay,
    misses: List<List<String>>? = null,
) {
    var unit by rememberSaveable { mutableStateOf(defaultUnit) }
    var onlyDifferences by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val partNumbers = products.map { it.partNumber }
    val allRows = remember(products, unit) { specRows(products, unit) }
    val visibleRows =
        when {
            expanded && onlyDifferences -> allRows.filter { it.differs }
            expanded -> allRows
            else -> collapsedRows(allRows)
        }
    val scroll = rememberScrollState()

    DataPanel {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(title, modifier = Modifier.weight(1f))
            UnitSwitch(unit, onChange = { unit = it })
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RowLabel("Model")
            Cells(partNumbers, scroll, comparing = true, PartNumberStyle.copy(fontSize = 13.sp))
        }
        misses?.let { lists ->
            Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiaryContainer)) {
                RowLabel("Does not meet", color = MaterialTheme.colorScheme.onTertiaryContainer)
                Cells(
                    lists.map { it.joinToString("\n") },
                    scroll,
                    comparing = true,
                    MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onTertiaryContainer),
                )
            }
        }
        visibleRows.forEach { row ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ValueRow(row, partNumbers, scroll, comparing = true)
        }
        if (visibleRows.isEmpty()) {
            Text(
                "These models have the same values in every row.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(14.dp),
            )
        }
        ExpandRow(if (expanded) "Show less" else "All ${allRows.size} specifications", onToggle = { expanded = !expanded })
        if (expanded) {
            FilterChip(
                selected = onlyDifferences,
                onClick = { onlyDifferences = !onlyDifferences },
                label = { Text("Only differences") },
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
            )
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
private fun ValueRow(row: SpecRow, partNumbers: List<String>, scroll: ScrollState, comparing: Boolean) {
    val spoken =
        if (comparing) {
            row.label + ": " + partNumbers.zip(row.values).joinToString(", ") { (part, value) -> "$part, $value" }
        } else {
            row.label + ": " + row.values.first()
        }
    // One announcement per row: TalkBack would otherwise read a bare figure with nothing saying what it measures.
    Row(modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }) {
        RowLabel(row.label)
        Cells(row.values, scroll, comparing, SpecValueStyle)
    }
}

@Composable
private fun Cells(values: List<String>, scroll: ScrollState, comparing: Boolean, style: TextStyle) {
    // Every row shares one scroll state, so a swipe on any row moves all the columns together.
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.horizontalScroll(scroll)) {
            values.forEach { value ->
                Text(
                    value,
                    style = style,
                    modifier =
                        (if (comparing) Modifier.width(COLUMN_WIDTH) else Modifier)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}
