package com.series76.assistant.ui.chat

import com.series76.assistant.data.remote.CatalogEntry

/*
 * Two ways to reach a part number without knowing its format: completing what was typed, and building one from the
 * choices that exist. Both only ever offer numbers from the catalog, so neither can produce one that is not there.
 */

private const val MAX_COMPLETIONS = 6

// The start of a part number at the end of the draft, however it is being typed: 763, 763a00 113, 763A00-11320000/.
private val TRAILING_PART_NUMBER = Regex("(?i)\\b76[1-7][0-9a-z]*(?:[-\\s/]+[0-9a-z]+)*[-\\s/]*$")
private val NOT_ALPHANUMERIC = Regex("[^0-9A-Za-z]")

private fun compact(text: String) = text.replace(NOT_ALPHANUMERIC, "").uppercase()

/** Catalog part numbers that the end of the draft could be the start of. Empty once it names one completely. */
fun completions(draft: String, catalog: List<CatalogEntry>): List<String> {
    val typed = TRAILING_PART_NUMBER.find(draft)?.value?.let(::compact) ?: return emptyList()
    val matches = catalog.map { it.partNumber }.filter { compact(it).startsWith(typed) }
    // A finished number ends with the revision letter, or stops just before it.
    val finished = matches.any { compact(it) == typed || compact(it).removeSuffix("A") == typed }
    return if (finished && matches.size == 1) emptyList() else matches.take(MAX_COMPLETIONS)
}

/** The draft with the part number being typed replaced by the one picked. */
fun complete(draft: String, partNumber: String): String {
    val match = TRAILING_PART_NUMBER.find(draft) ?: return draft
    return draft.substring(0, match.range.first) + partNumber + " "
}

/** The choices made so far in the builder. Each step only offers what the earlier ones leave in the catalog. */
data class PartNumberChoice(val size: String? = null, val enclosure: String? = null, val powerSupply: String? = null)

fun size(partNumber: String) = partNumber.take(3)

fun sizeOptions(catalog: List<CatalogEntry>): List<String> = catalog.map { size(it.partNumber) }.distinct().sorted()

fun enclosureOptions(catalog: List<CatalogEntry>, choice: PartNumberChoice): List<String> =
    catalog.filter { size(it.partNumber) == choice.size }.map { it.enclosure }.distinct().sorted()

fun powerSupplyOptions(catalog: List<CatalogEntry>, choice: PartNumberChoice): List<String> =
    catalog
        .filter { size(it.partNumber) == choice.size && it.enclosure == choice.enclosure }
        .map { it.powerSupply }
        .distinct()
        .sortedBy { POWER_SUPPLY_ORDER.indexOf(it) }

fun builtPartNumbers(catalog: List<CatalogEntry>, choice: PartNumberChoice): List<String> =
    catalog
        .filter {
            size(it.partNumber) == choice.size && it.enclosure == choice.enclosure && it.powerSupply == choice.powerSupply
        }
        .map { it.partNumber }

// The data sheet's own order, from the most common supply to the least.
private val POWER_SUPPLY_ORDER =
    listOf("110v_single_phase", "220v_single_phase", "220v_three_phase", "24v_ac_dc", "24v_dc")
