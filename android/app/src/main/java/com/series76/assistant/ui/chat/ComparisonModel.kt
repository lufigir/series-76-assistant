package com.series76.assistant.ui.chat

import com.series76.assistant.data.remote.ActuatorData
import com.series76.assistant.data.remote.Rating
import com.series76.assistant.data.remote.Requirements
import com.series76.assistant.data.remote.UnmetRequirement
import kotlinx.serialization.json.JsonPrimitive

enum class TorqueDisplay(val label: String, val apiName: String) {
    NM("Nm", "nm"),
    IN_LB("in-lb", "in_lb"),
    FT_LB("ft-lb", "ft_lb"),
}

data class SpecRow(val label: String, val values: List<String>) {
    val differs: Boolean get() = values.distinct().size > 1
}

const val NOT_PUBLISHED = "—"

/** N/A is what the data sheet prints for a part number it lists without data, which differs from a blank cell. */
const val NOT_AVAILABLE = "N/A"

private val LABELS =
    mapOf(
        "weatherproof" to "Weatherproof",
        "explosionproof" to "Explosionproof",
        "110v" to "110 V",
        "220v" to "220 V",
        "24v" to "24 V",
        "24v_ac" to "24 V AC",
        "24v_dc" to "24 V DC",
        "110v_single_phase" to "110 V single phase",
        "220v_single_phase" to "220 V single phase",
        "220v_three_phase" to "220 V three phase",
        "24v_ac_dc" to "24 V AC/DC",
        "on_off" to "On/off",
        "modulating" to "Modulating",
        "on_off_and_modulating" to "On/off and modulating",
        "unspecified" to "Not stated",
        "not_specified" to "not stated",
        "60hz" to "60 Hz",
        "50hz" to "50 Hz",
        "yes" to "yes",
        "no" to "no",
    )

private val UNMET_LABELS =
    mapOf(
        "min_torque_nm" to "Torque (Nm)",
        "min_torque_in_lb" to "Torque (in-lb)",
        "min_torque_ft_lb" to "Torque (ft-lb)",
        "min_duty_pct" to "Duty (%)",
        "max_speed_sec" to "Speed (s)",
        "max_fla" to "FLA (A)",
        "max_lra" to "LRA (A)",
        "max_motor_watts" to "Motor (W)",
        "min_cycles_per_hour" to "Cycles per hour",
        "min_starts_per_hour" to "Starts per hour",
        "csa_certified" to "CSA certified",
        "service" to "Service",
    )

private fun label(raw: String): String = LABELS[raw] ?: raw

/** A value exactly as received, without the ".0" that a JSON float adds to a whole number. */
private fun number(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

private fun primitive(value: JsonPrimitive?): String {
    if (value == null || value.content == "null") return "not published"
    return value.content.toDoubleOrNull()?.let(::number) ?: label(value.content)
}

fun unmetText(unmet: UnmetRequirement): String =
    "${UNMET_LABELS[unmet.requirement] ?: unmet.requirement}: " +
        "needs ${primitive(unmet.required)}, has ${primitive(unmet.actual)}"

fun requirementChips(requirements: Requirements): List<String> =
    buildList {
        requirements.enclosure?.let { add(label(it)) }
        requirements.powerSupply?.let { add(label(it)) }
        requirements.service?.let { add(label(it)) }
        requirements.frequency?.let { add(label(it)) }
        requirements.minTorque?.let { torque ->
            val unit = TorqueDisplay.entries.firstOrNull { it.apiName == torque.unit }?.label ?: torque.unit
            add("≥ ${number(torque.value)} $unit")
        }
        requirements.minDutyPct?.let { add("Duty ≥ $it%") }
        requirements.maxSpeedSec?.let { add("Speed ≤ $it s") }
        requirements.maxFla?.let { add("FLA ≤ ${number(it)} A") }
        requirements.maxLra?.let { add("LRA ≤ ${number(it)} A") }
        requirements.maxMotorWatts?.let { add("Motor ≤ $it W") }
        requirements.minCyclesPerHour?.let { add("≥ $it cycles/h") }
        requirements.minStartsPerHour?.let { add("≥ $it starts/h") }
        if (requirements.csaCertified) add("CSA certified")
    }

private fun torque(rating: Rating, unit: TorqueDisplay): String =
    when (unit) {
        TorqueDisplay.NM -> rating.torqueNm.toString()
        TorqueDisplay.IN_LB -> rating.torqueInLb.toString()
        TorqueDisplay.FT_LB -> rating.torqueFtLb.toString()
    }

/**
 * The same rows in the same order for every product, so that any two cards read alike. A row exists when at
 * least one product has a value for it, and a product without one shows a blank, never a value from elsewhere.
 */
fun specRows(products: List<ActuatorData>, unit: TorqueDisplay): List<SpecRow> {
    fun missing(product: ActuatorData) = if (product.notAvailable) NOT_AVAILABLE else NOT_PUBLISHED

    fun row(name: String, value: (ActuatorData) -> String?): SpecRow? =
        if (products.all { value(it) == null }) null
        else SpecRow(name, products.map { value(it) ?: missing(it) })

    val ratingRows =
        listOf("on_off" to "on/off", "modulating" to "modulating", "unspecified" to "service not stated")
            .flatMap { (service, name) ->
                listOf(
                    row("Torque, $name (${unit.label})") { product ->
                        product.ratings.firstOrNull { it.service == service }?.let { torque(it, unit) }
                    },
                    row("Duty, $name (%)") { product ->
                        product.ratings.firstOrNull { it.service == service }?.dutyPct?.toString()
                    },
                )
            }

    val electricalRows =
        listOf("60hz" to " at 60 Hz", "50hz" to " at 50 Hz", "none" to "").flatMap { (frequency, suffix) ->
            fun electrical(product: ActuatorData) = product.electricals.firstOrNull { it.frequency == frequency }
            listOf(
                row("Speed$suffix (s)") { electrical(it)?.speedSec?.toString() },
                row("FLA$suffix (A)") { electrical(it)?.fla?.toString() },
                row("LRA$suffix (A)") { electrical(it)?.lra?.toString() },
            )
        }

    return listOfNotNull(
        row("Enclosure") { label(it.enclosure) },
        row("Power supply") { label(it.powerSupply) },
        row("Table service") { label(it.service) },
        row("CSA certified") { if (it.notCsaCertified) "No" else "Yes" },
    ) +
        ratingRows.filterNotNull() +
        electricalRows.filterNotNull() +
        listOfNotNull(
            row("Cycles per hour") { it.cyclesPerHour?.toString() },
            row("Starts per hour") { it.startsPerHour?.toString() },
            row("Motor (W)") { it.motorWatts?.toString() },
        )
}

/** One block of a data sheet: what the columns are, and a row of published figures per measure. */
data class SpecTable(val columns: List<String>, val rows: List<SpecRow>)

enum class OutcomeTone { CAUTION, NEGATIVE, NEUTRAL }

/** A note for the turns that did not simply answer. A normal answer needs no label to say that it is one. */
fun outcomeNote(outcome: String?): String? =
    when (outcome) {
        "no_match" -> "No exact match"
        "invalid_part_number" -> "Part number not found"
        "missing_data" -> "Not published"
        "ambiguous" -> "Needs more detail"
        "out_of_scope" -> "Outside the catalog"
        else -> null
    }

fun conversationTitle(messages: List<ChatMessage>, loading: Boolean): String =
    messages.firstOrNull { it.fromUser }?.text ?: if (loading) "" else "New conversation"

private const val LONG_WAIT_SECONDS = 10

/** Past ten seconds a person's attention drifts, so the wait has to say that it is still going and for how long. */
fun workingText(elapsedSeconds: Int): String =
    if (elapsedSeconds < LONG_WAIT_SECONDS) "Checking the data tables"
    else "Still checking. This can take up to a minute."

fun outcomeTone(outcome: String?): OutcomeTone =
    when (outcome) {
        "no_match", "missing_data", "ambiguous" -> OutcomeTone.CAUTION
        "invalid_part_number" -> OutcomeTone.NEGATIVE
        else -> OutcomeTone.NEUTRAL
    }

fun enclosureLabel(raw: String): String = label(raw)

fun powerSupplyLabel(raw: String): String = label(raw)

/**
 * The figures an engineer decides on, read straight from the row: nothing here is computed or rounded. Every
 * service and frequency appears, because the card cannot tell which one a follow-up question was about.
 */
fun specTables(product: ActuatorData, unit: TorqueDisplay): List<SpecTable> {
    val ratings = product.ratings
    val electricals = product.electricals
    return listOfNotNull(
        SpecTable(
            columns = ratings.map { if (it.service == "unspecified") "Service not stated" else label(it.service) },
            rows =
                listOf(
                    SpecRow("Torque (${unit.label})", ratings.map { torque(it, unit) }),
                    SpecRow("Duty (%)", ratings.map { it.dutyPct.toString() }),
                ),
        ).takeIf { ratings.isNotEmpty() },
        SpecTable(
            columns = electricals.map { if (it.frequency == "none") "All" else label(it.frequency) },
            rows =
                listOf(
                    SpecRow("Speed (s)", electricals.map { it.speedSec.toString() }),
                    SpecRow("FLA (A)", electricals.map { it.fla.toString() }),
                    SpecRow("LRA (A)", electricals.map { it.lra.toString() }),
                ),
        ).takeIf { electricals.isNotEmpty() },
    )
}

private val SHOWN_ON_THE_CARD = listOf("Enclosure", "Power supply", "Torque,", "Duty,", "Speed", "FLA", "LRA")

fun extraRows(product: ActuatorData, unit: TorqueDisplay): List<SpecRow> =
    specRows(listOf(product), unit).filter { row -> SHOWN_ON_THE_CARD.none(row.label::startsWith) }

private val KEY_ROWS = listOf("Torque,", "Duty,", "Speed", "FLA", "CSA")

/**
 * What a collapsed comparison shows: the measures that usually decide, minus any all models share. A short table
 * also keeps the part numbers in sight, which is why torque stays even when it matches. Identical models keep every
 * key row, since an empty table would say nothing.
 */
fun collapsedRows(rows: List<SpecRow>): List<SpecRow> {
    val key = rows.filter { row -> KEY_ROWS.any(row.label::startsWith) }
    return key.filter { it.differs || it.label.startsWith("Torque,") }.takeIf { selected -> selected.any { it.differs } }
        ?: key
}
