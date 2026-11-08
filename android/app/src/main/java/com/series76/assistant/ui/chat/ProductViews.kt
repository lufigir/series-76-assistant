package com.series76.assistant.ui.chat

import com.series76.assistant.ui.components.keepPartNumbersWhole
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.res.painterResource
import com.series76.assistant.R
import com.series76.assistant.ui.components.IconBadge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
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

// Condensed density, about 36 to 40 dp a row: Material's tables use 52, and dense views go down to 40. These rows are
// read, not tapped, so they need no 48 dp touch target.
private val ROW_PADDING = 4.dp
private val PANEL_PADDING = 12.dp

// Wide enough that another product runs off a phone's edge: the cut-off column is what says the table scrolls.
private val COLUMN_WIDTH = 156.dp

// The label column takes a share of the width rather than a fixed one, so "Torque (Nm)" stays on one line at large
// text sizes instead of doubling the row's height.
private const val LABEL_WEIGHT = 1.1f
private const val PAIR_COLUMNS = 2f

/** A set of models from one answer. How many there are decides how they read best on a phone. */
@Composable
fun ProductResults(
    title: String,
    products: List<ActuatorData>,
    defaultUnit: TorqueDisplay,
    requirements: Requirements?,
    unmetByModel: List<List<UnmetRequirement>?>,
    folded: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(!folded) }
    if (!open) {
        FoldedResults(products, onOpen = { open = true })
        return
    }
    val checks = remember(requirements, unmetByModel) { requirementChecks(requirements, unmetByModel) }
    when (resultLayout(products.size)) {
        ResultLayout.CARD -> ProductCard(products.single(), defaultUnit, checks)
        ResultLayout.PAIR -> ComparisonTable(title, products, defaultUnit, checks)
        ResultLayout.LIST -> ProductList(title, products, defaultUnit, checks)
    }
}

/** Models an earlier answer already showed in full: who they are, and the sheet one tap away. */
@Composable
private fun FoldedResults(products: List<ActuatorData>, onOpen: () -> Unit) {
    DataPanel {
        products.forEachIndexed { index, product ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Identity(product)
        }
        ExpandRow(if (products.size == 1) "Show data sheet" else "Show comparison", onToggle = onOpen)
    }
}

private fun singleModel(checks: List<RequirementCheck>, index: Int) =
    checks.map { it.copy(results = listOf(it.results[index])) }

/** One actuator, read like a data sheet: identity, then its figures laid out by service and frequency. */
@Composable
private fun ProductCard(product: ActuatorData, defaultUnit: TorqueDisplay, checks: List<RequirementCheck>) {
    var unit by rememberSaveable { mutableStateOf(defaultUnit) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val reveal = remember { BringIntoViewRequester() }
    // The rows open below the fold of a long answer, so the list follows them instead of leaving them off screen.
    LaunchedEffect(expanded) { if (expanded) reveal.bringIntoView() }

    DataPanel {
        Identity(product)
        if (product.notAvailable) {
            Notice("The data sheet lists this model as N/A, so it publishes no ratings for it.")
        } else {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ModelDetails(product, unit, checks, onUnit = if (product.ratings.isNotEmpty()) ({ unit = it }) else null)
            // Above its toggle, so the button that opened the rows stays below them, where it closes them too.
            if (expanded) ExtraSpecs(remember(product, unit) { extraRows(product, unit) }, Modifier.bringIntoViewRequester(reveal))
            ExpandRow(if (expanded) "Show less" else "More specifications", onToggle = { expanded = !expanded })
        }
    }
}

@Composable
private fun Identity(product: ActuatorData) {
    Row(
        modifier = Modifier.padding(PANEL_PADDING),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(painterResource(enclosureIcon(product.enclosure)), size = 28.dp)
        IdentityText(product)
    }
}

private fun enclosureIcon(enclosure: String): Int =
    if (enclosure == "explosionproof") R.drawable.ic_shield else R.drawable.ic_water_drop

@Composable
private fun IdentityText(product: ActuatorData) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // One line that shrinks at large text sizes: a code split across lines is easy to misread.
        Text(
            product.partNumber,
            style = PartNumberStyle,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = PartNumberStyle.fontSize),
        )
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
private fun ModelDetails(
    product: ActuatorData,
    unit: TorqueDisplay,
    checks: List<RequirementCheck>,
    onUnit: ((TorqueDisplay) -> Unit)? = null,
) {
    if (checks.isNotEmpty()) {
        Column(modifier = Modifier.padding(start = PANEL_PADDING, end = PANEL_PADDING, bottom = 12.dp)) {
            SectionLabel("Your requirements", modifier = Modifier.padding(bottom = 4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            checks.forEach { check ->
                val result = check.results.single()
                Row(
                    modifier =
                        Modifier.fillMaxWidth().padding(vertical = ROW_PADDING).clearAndSetSemantics {
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
                    CheckIcon(result.state)
                    Text(
                        result.text,
                        style = SpecValueStyle,
                        color = checkColor(result.state),
                    )
                }
            }
        }
    }
    // The unit switch sits in the corner of the torque table, the only table it changes, instead of a row of its own.
    remember(product, unit) { specTables(product, unit) }.forEachIndexed { index, table ->
        SpecTableView(table, corner = if (index == 0 && onUnit != null) ({ UnitSwitch(unit, onUnit) }) else null)
    }
}

@Composable
private fun checkColor(state: CheckState): Color =
    when (state) {
        CheckState.UNMET -> MaterialTheme.colorScheme.tertiary
        CheckState.MET -> MaterialTheme.colorScheme.secondary
        CheckState.NOT_CHECKED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

/** A shape as well as a color, so met and missed read apart without telling graphite from amber. */
@Composable
private fun CheckIcon(state: CheckState) {
    val icon =
        when (state) {
            CheckState.MET -> Icons.Filled.CheckCircle
            CheckState.UNMET -> Icons.Filled.Warning
            CheckState.NOT_CHECKED -> return
        }
    Icon(icon, contentDescription = null, tint = checkColor(state), modifier = Modifier.padding(end = 6.dp).size(16.dp))
}

@Composable
private fun SpecTableView(table: SpecTable, corner: (@Composable () -> Unit)? = null) {
    Column(modifier = Modifier.padding(start = PANEL_PADDING, end = PANEL_PADDING, top = 8.dp, bottom = 8.dp)) {
        BoxWithConstraints {
            val headerStyle = fittedHeaderStyle(table.columns, cellWidth = maxWidth / (LABEL_WEIGHT + table.columns.size) - 8.dp)
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(LABEL_WEIGHT)) { corner?.invoke() }
                table.columns.forEach { column ->
                    Text(
                        column,
                        style = headerStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        table.rows.forEach { row ->
            val spoken = row.label + ": " + table.columns.zip(row.values).joinToString(", ") { (c, v) -> "$c, $v" }
            SpecLine(row.label, row.values, spoken, valueWeight = 1f)
        }
    }
}

/**
 * One size for every column heading, shrunk only as far as the widest needs. At large text sizes "Modulating" would
 * otherwise break mid-word, and shrinking each heading on its own left "On/off" and "Modulating" in two sizes.
 */
@Composable
private fun fittedHeaderStyle(columns: List<String>, cellWidth: Dp): TextStyle {
    val style = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val available = with(LocalDensity.current) { cellWidth.roundToPx() }
    return remember(columns, available, style, measurer) {
        val widest = columns.maxOfOrNull { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width } ?: 0
        if (widest <= available || available <= 0) style else style.copy(fontSize = style.fontSize * (available.toFloat() / widest))
    }
}

/** A label and its figures, read as one line by TalkBack, which would otherwise announce a bare figure. */
@Composable
private fun SpecLine(label: String, values: List<String>, spoken: String, valueWeight: Float) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = ROW_PADDING).clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(LABEL_WEIGHT),
        )
        values.forEach { value ->
            Text(value, style = SpecValueStyle, textAlign = TextAlign.End, modifier = Modifier.weight(valueWeight).padding(start = 8.dp))
        }
    }
}

/** The rest of the data sheet, laid out like the table above it, with each value free to wrap at large text sizes. */
@Composable
private fun ExtraSpecs(rows: List<SpecRow>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(modifier = Modifier.padding(horizontal = PANEL_PADDING, vertical = 8.dp)) {
            rows.forEach { row -> SpecLine(row.label, row.values, row.label + ": " + row.values.first(), valueWeight = 1f) }
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
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(onClickLabel = if (open) "Hide details" else "Show details") { open = !open }
                    .padding(horizontal = PANEL_PADDING, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    keepPartNumbersWhole(product.partNumber),
                    style = PartNumberStyle.copy(fontSize = 15.sp),
                    modifier = Modifier.weight(1f),
                )
                // Nothing else says the row opens, so the chevron does.
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(summaryLine(product, unit), style = SpecValueStyle)
            if (misses.isNotEmpty()) {
                Text(
                    "Misses " + misses.joinToString { it.label },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
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
        // The same inset as the card's content on every side, so the pill's border lines up with the rows below it
        // instead of crowding the card's rounded corner.
        modifier = Modifier.fillMaxWidth().padding(horizontal = PANEL_PADDING, vertical = 8.dp),
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
                    UnitSwitch(unit, onChange = onUnit, modifier = Modifier.padding(end = 4.dp))
                }
                FilterChip(
                    selected = onlyDifferences,
                    onClick = { onlyDifferences = !onlyDifferences },
                    label = { Text("Only differences") },
                    modifier = Modifier.padding(horizontal = 16.dp),
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
                                modifier = Modifier.padding(16.dp),
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
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
private fun ModelHeader(partNumbers: List<String>, scroll: ScrollState, fit: ColumnFit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RowLabel("Model", fit)
        val shown = if (fit == ColumnFit.EQUAL) partNumbers.map(::partNumberOnTwoLines) else partNumbers
        val style = PartNumberStyle.copy(fontSize = 13.sp, lineHeight = 17.sp)
        // Two lines at most, broken at the dash; at large text sizes the face shrinks rather than break mid-code.
        ColumnsRow(scroll, fit) { cellModifier ->
            shown.forEach { partNumber ->
                Text(
                    partNumber,
                    style = style,
                    maxLines = 2,
                    autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize),
                    modifier = cellModifier(this),
                )
            }
        }
    }
}

@Composable
private fun CheckRow(check: RequirementCheck, partNumbers: List<String>, scroll: ScrollState, fit: ColumnFit) {
    val spoken = check.label + ": " + partNumbers.zip(check.results).joinToString(", ") { (part, r) -> "$part, ${r.text}" }
    Row(modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }) {
        RowLabel(check.label, fit)
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
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
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
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .background(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.shapes.small)
                .padding(12.dp),
    )
}

/**
 * The torque unit as one small pill that opens a menu. Three segments did not fit a table's corner at large text
 * sizes, and the unit is changed rarely, so one tap more costs little.
 */
@Composable
private fun UnitSwitch(unit: TorqueDisplay, onChange: (TorqueDisplay) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier =
                Modifier.clip(MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                    .clickable(onClickLabel = "Change the torque unit") { open = true }
                    .heightIn(min = 32.dp)
                    .padding(start = 10.dp, end = 4.dp)
                    .semantics { contentDescription = "Torque unit, ${unit.label}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(unit.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TorqueDisplay.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onChange(option)
                        open = false
                    },
                    trailingIcon =
                        if (option == unit) ({ Icon(Icons.Filled.Check, contentDescription = "Selected") }) else null,
                )
            }
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = PANEL_PADDING, vertical = 10.dp),
    )
}

@Composable
private fun RowScope.RowLabel(text: String, fit: ColumnFit, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    // Columns that share the width let the label share it too, so large text wraps it less. Scrolling columns need a
    // fixed label, which stays put while they move.
    val width = if (fit == ColumnFit.EQUAL) Modifier.weight(LABEL_WEIGHT) else Modifier.width(LABEL_WIDTH)
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = width.padding(horizontal = PANEL_PADDING, vertical = 4.dp),
    )
}

@Composable
private fun ValueRow(row: SpecRow, partNumbers: List<String>, scroll: ScrollState, fit: ColumnFit) {
    val spoken = row.label + ": " + partNumbers.zip(row.values).joinToString(", ") { (part, value) -> "$part, $value" }
    // One announcement per row: TalkBack would otherwise read a bare figure with nothing saying what it measures.
    Row(modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }) {
        RowLabel(row.label, fit)
        Cells(row.values, scroll, fit, SpecValueStyle)
    }
}

/** EQUAL shares the width, for two models. SCROLL gives each a fixed column. */
private enum class ColumnFit { EQUAL, SCROLL }

@Composable
private fun RowScope.Cells(values: List<String>, scroll: ScrollState, fit: ColumnFit, style: TextStyle) {
    ColumnsRow(scroll, fit) { cellModifier ->
        values.forEach { value -> Text(value, style = style, modifier = cellModifier(this)) }
    }
}

@Composable
private fun RowScope.ColumnsRow(
    scroll: ScrollState,
    fit: ColumnFit,
    content: @Composable RowScope.(cellModifier: RowScope.() -> Modifier) -> Unit,
) {
    val padded = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    if (fit == ColumnFit.EQUAL) {
        // Weighted against the label, one share per model, so label and values divide the width between them.
        Row(modifier = Modifier.weight(PAIR_COLUMNS)) { content { Modifier.weight(1f).then(padded) } }
    } else {
        // Every row shares one scroll state, so a swipe on any row moves all the columns together.
        Box(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.horizontalScroll(scroll)) {
                content { Modifier.width(COLUMN_WIDTH).then(padded) }
            }
        }
    }
}
