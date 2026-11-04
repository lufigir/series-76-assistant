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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.series76.assistant.data.remote.ActuatorData
import com.series76.assistant.ui.components.CapsLabel
import com.series76.assistant.ui.theme.KeyFigureStyle
import com.series76.assistant.ui.theme.PartNumberStyle
import com.series76.assistant.ui.theme.SpecValueStyle

private val LABEL_WIDTH = 124.dp

// Wide enough that a second product runs off a phone's edge: the cut-off column is what says the table scrolls.
private val COLUMN_WIDTH = 168.dp

private const val SOURCE_NOTE = "Values from the Series 76 electrical data tables."

/** One actuator, read like a data sheet: identity, the figures that decide, and the full table on request. */
@Composable
fun ProductCard(product: ActuatorData, defaultUnit: TorqueDisplay) {
    var unit by rememberSaveable { mutableStateOf(defaultUnit) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val figures = remember(product, unit) { keyFigures(product, unit) }
    val rows = remember(product, unit) { specRows(listOf(product), unit) }

    DataPanel {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(product.partNumber, style = PartNumberStyle)
            IdentityTags(product)
            if (product.notAvailable) {
                Notice("The data sheet lists this model as N/A, so it publishes no ratings for it.")
            } else {
                FigureGrid(figures)
                UnitSwitch(unit, onChange = { unit = it })
            }
        }
        if (!product.notAvailable) {
            ExpandRow(expanded, count = rows.size, onToggle = { expanded = !expanded })
            if (expanded) {
                val scroll = rememberScrollState()
                rows.forEachIndexed { index, row ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ValueRow(row, listOf(product.partNumber), scroll, comparing = false, shaded = index % 2 == 1)
                }
            }
        }
        SourceNote()
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
            else -> allRows.filter { isKeyRow(it.label) }
        }
    val scroll = rememberScrollState()

    DataPanel {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CapsLabel(title)
            UnitSwitch(unit, onChange = { unit = it })
        }
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
        visibleRows.forEachIndexed { index, row ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ValueRow(row, partNumbers, scroll, comparing = true, shaded = index % 2 == 1)
        }
        if (visibleRows.isEmpty()) {
            Text(
                "These models have the same values in every row.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(14.dp),
            )
        }
        ExpandRow(expanded, count = allRows.size, onToggle = { expanded = !expanded })
        if (expanded) {
            FilterChip(
                selected = onlyDifferences,
                onClick = { onlyDifferences = !onlyDifferences },
                label = { Text("Only differences") },
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
        SourceNote()
    }
}

@Composable
private fun DataPanel(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                .animateContentSize(),
    ) {
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdentityTags(product: ActuatorData) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Tag(enclosureLabel(product.enclosure))
        Tag(powerSupplyLabel(product.powerSupply))
        if (product.notCsaCertified) Tag("Not CSA certified", warning = true)
    }
}

@Composable
private fun Tag(text: String, warning: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color =
            if (warning) MaterialTheme.colorScheme.onTertiaryContainer
            else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier =
            Modifier.background(
                if (warning) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.shapes.small,
            )
                .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier =
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.shapes.small)
                .padding(10.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FigureGrid(figures: List<KeyFigure>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = 2,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        figures.forEach { figure ->
            Column(
                modifier =
                    Modifier.weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
                        .padding(10.dp)
                        .clearAndSetSemantics {
                            contentDescription = "${figure.label}: ${figure.value} ${figure.unit}"
                        },
            ) {
                CapsLabel(figure.label)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(figure.value, style = KeyFigureStyle)
                    Text(
                        figure.unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun UnitSwitch(unit: TorqueDisplay, onChange: (TorqueDisplay) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CapsLabel("Torque in")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.heightIn(max = 36.dp)) {
            TorqueDisplay.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == unit,
                    onClick = { onChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, TorqueDisplay.entries.size),
                    label = { Text(option.label, style = MaterialTheme.typography.labelMedium) },
                    icon = {},
                )
            }
        }
    }
}

@Composable
private fun ExpandRow(expanded: Boolean, count: Int, onToggle: () -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        if (expanded) "Show less" else "All $count specifications",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(14.dp),
    )
}

@Composable
private fun SourceNote() {
    Text(
        SOURCE_NOTE,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
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
private fun ValueRow(
    row: SpecRow,
    partNumbers: List<String>,
    scroll: ScrollState,
    comparing: Boolean,
    shaded: Boolean,
) {
    val spoken =
        if (comparing) {
            row.label + ": " + partNumbers.zip(row.values).joinToString(", ") { (part, value) -> "$part, $value" }
        } else {
            row.label + ": " + row.values.first()
        }
    // One announcement per row: TalkBack would otherwise read a bare figure with nothing saying what it measures.
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(if (shaded) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
                .clearAndSetSemantics { contentDescription = spoken },
    ) {
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
