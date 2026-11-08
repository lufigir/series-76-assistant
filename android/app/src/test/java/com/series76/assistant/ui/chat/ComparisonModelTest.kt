package com.series76.assistant.ui.chat

import com.series76.assistant.data.remote.ActuatorData
import com.series76.assistant.data.remote.Electrical
import com.series76.assistant.data.remote.Rating
import com.series76.assistant.data.remote.Requirements
import com.series76.assistant.data.remote.TorqueRequirement
import com.series76.assistant.data.remote.UnmetRequirement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun actuator(
    partNumber: String = "761A00-11300000/A",
    torqueNm: Int = 80,
    electricals: List<Electrical> = listOf(Electrical("60hz", 20, 1.2, 1.4)),
    ratings: List<Rating> = listOf(Rating("on_off", 690, torqueNm, 57.5, 40)),
    notAvailable: Boolean = false,
    notCsaCertified: Boolean = false,
) = ActuatorData(
    partNumber = partNumber,
    enclosure = "weatherproof",
    powerSupply = "110v_single_phase",
    service = "on_off_and_modulating",
    notAvailable = notAvailable,
    notCsaCertified = notCsaCertified,
    cyclesPerHour = 190,
    startsPerHour = 960,
    motorWatts = null,
    ratings = ratings,
    electricals = electricals,
)

class ComparisonModelTest {
    private fun rows(vararg products: ActuatorData, unit: TorqueDisplay = TorqueDisplay.NM) =
        specRows(products.toList(), unit)

    @Test
    fun `values appear exactly as the API sent them`() {
        val rows = rows(actuator()).associate { it.label to it.values.single() }
        assertEquals("80", rows["Torque, on/off (Nm)"])
        assertEquals("40", rows["Duty, on/off (%)"])
        assertEquals("1.2", rows["FLA at 60 Hz (A)"])
        assertEquals("1.4", rows["LRA at 60 Hz (A)"])
    }

    @Test
    fun `the unit switch reads the published figure, it does not convert`() {
        assertEquals("690", rows(actuator(), unit = TorqueDisplay.IN_LB).first { "Torque" in it.label }.values.single())
        assertEquals("57.5", rows(actuator(), unit = TorqueDisplay.FT_LB).first { "Torque" in it.label }.values.single())
    }

    @Test
    fun `a value only one product has leaves a blank in the other, never a copy`() {
        val with50 = actuator(electricals = listOf(Electrical("50hz", 23, 1.1, 1.3)))
        val row = rows(actuator(), with50).first { it.label == "FLA at 50 Hz (A)" }
        assertEquals(listOf(NOT_PUBLISHED, "1.1"), row.values)
    }

    @Test
    fun `a part number listed without data reads as N-A, not as blank`() {
        val listed = actuator("761A00-11330000/A", ratings = emptyList(), electricals = emptyList(), notAvailable = true)
        val row = rows(actuator(), listed).first { it.label == "Torque, on/off (Nm)" }
        assertEquals(listOf("80", NOT_AVAILABLE), row.values)
    }

    @Test
    fun `a row nobody has is not shown`() {
        assertTrue(rows(actuator()).none { it.label == "Motor (W)" })
        assertTrue(rows(actuator()).none { "modulating" in it.label })
    }

    @Test
    fun `the missing CSA certification is visible`() {
        val row = rows(actuator(notCsaCertified = true)).first { it.label == "CSA certified" }
        assertEquals("No", row.values.single())
    }

    @Test
    fun `differences are detected per row`() {
        val rows = rows(actuator(torqueNm = 80), actuator(torqueNm = 120))
        assertTrue(rows.first { it.label == "Torque, on/off (Nm)" }.differs)
        assertFalse(rows.first { it.label == "Enclosure" }.differs)
    }

    @Test
    fun `an unmet requirement says what was needed and what it has`() {
        val torque = UnmetRequirement("min_torque_nm", JsonPrimitive(500.0), JsonPrimitive(120))
        assertEquals("Torque (Nm): needs 500, has 120", unmetText(torque))
        val service = UnmetRequirement("service", JsonPrimitive("on_off"), JsonPrimitive("not_specified"))
        assertEquals("Service: needs On/off, has not stated", unmetText(service))
        val missing = UnmetRequirement("min_starts_per_hour", JsonPrimitive(100), null)
        assertEquals("Starts per hour: needs 100, has not published", unmetText(missing))
    }

    @Test
    fun `requirements become short chips, and what was not asked stays out`() {
        val chips =
            requirementChips(
                Requirements(
                    enclosure = "explosionproof",
                    powerSupply = "24v",
                    minTorque = TorqueRequirement(1000.0, "nm"),
                ),
            )
        assertEquals(listOf("Explosionproof", "24 V", "≥ 1,000 Nm"), chips)
        assertTrue(requirementChips(Requirements()).isEmpty())
        assertNull(requirementChips(Requirements()).firstOrNull())
    }

    @Test
    fun `a data sheet table reads as published, with services and frequencies as columns`() {
        val tables =
            specTables(
                actuator(
                    ratings = listOf(Rating("on_off", 690, 80, 57.5, 40), Rating("modulating", 550, 65, 45.8, 35)),
                    electricals = listOf(Electrical("60hz", 20, 1.2, 1.4), Electrical("50hz", 23, 1.1, 1.3)),
                ),
                TorqueDisplay.IN_LB,
            )
        assertEquals(
            listOf(
                SpecTable(
                    listOf("On/off", "Modulating"),
                    listOf(SpecRow("Torque (in-lb)", listOf("690", "550")), SpecRow("Duty (%)", listOf("40", "35"))),
                ),
                SpecTable(
                    listOf("60 Hz", "50 Hz"),
                    listOf(
                        SpecRow("Speed (s)", listOf("20", "23")),
                        SpecRow("FLA (A)", listOf("1.2", "1.1")),
                        SpecRow("LRA (A)", listOf("1.4", "1.3")),
                    ),
                ),
            ),
            tables,
        )
    }

    @Test
    fun `a three phase rating says its service is not stated`() {
        val threePhase = actuator(ratings = listOf(Rating("unspecified", 690, 80, 57.5, 40)))
        assertEquals("Service not stated", specTables(threePhase, TorqueDisplay.NM).first().columns.single())
    }

    @Test
    fun `a table with a single speed has no frequency to name`() {
        val lowVoltage = actuator(electricals = listOf(Electrical("none", 20, 1.2, 1.4)))
        assertEquals("All", specTables(lowVoltage, TorqueDisplay.NM).last().columns.single())
    }

    @Test
    fun `a model without data has no tables to show`() {
        val listed = actuator(ratings = emptyList(), electricals = emptyList(), notAvailable = true)
        assertTrue(specTables(listed, TorqueDisplay.NM).isEmpty())
    }

    @Test
    fun `more specifications holds only what the card does not already show`() {
        assertEquals(
            listOf("Table service", "CSA certified", "Cycles per hour", "Starts per hour"),
            extraRows(actuator(), TorqueDisplay.NM).map { it.label },
        )
    }

    @Test
    fun `a collapsed comparison keeps torque and what differs, with the 50 Hz rows`() {
        val faster = actuator(torqueNm = 80, electricals = listOf(Electrical("50hz", 23, 1.1, 1.3)))
        val labels = collapsedRows(rows(actuator(torqueNm = 80), faster)).map { it.label }
        assertEquals(
            listOf("Torque, on/off (Nm)", "Speed at 60 Hz (s)", "FLA at 60 Hz (A)", "Speed at 50 Hz (s)", "FLA at 50 Hz (A)"),
            labels,
        )
    }

    @Test
    fun `a collapsed comparison of identical models still shows its key rows`() {
        val labels = collapsedRows(rows(actuator(), actuator())).map { it.label }
        assertTrue("Torque, on/off (Nm)" in labels)
        assertTrue("Duty, on/off (%)" in labels)
    }

    @Test
    fun `only an outcome the reader should notice gets a note`() {
        assertNull(outcomeNote("answered"))
        assertNull(outcomeNote("recommendation"))
        assertEquals("No exact match", outcomeNote("no_match"))
        assertEquals("Needs more detail", outcomeNote("ambiguous", hasResults = true))
        assertEquals(OutcomeTone.CAUTION, outcomeTone("no_match"))
        assertEquals(OutcomeTone.NEGATIVE, outcomeTone("invalid_part_number"))
        assertEquals(OutcomeTone.NEUTRAL, outcomeTone("out_of_scope"))
    }

    @Test
    fun `a clarifying question with nothing found carries no label, since the question says it`() {
        assertNull(outcomeNote("ambiguous", hasResults = false))
        assertEquals("Part number not found", outcomeNote("invalid_part_number", hasResults = false))
    }

    @Test
    fun `a conversation shows the backend's name, and its first question until it has one`() {
        val messages = listOf(ChatMessage("1", true, "Torque of 761A?"), ChatMessage("2", false, "It delivers 80 Nm."))
        assertEquals("761A00-11300000/A", conversationTitle("761A00-11300000/A", messages, loading = false))
        assertEquals("Torque of 761A?", conversationTitle(null, messages, loading = false))
        assertEquals("New conversation", conversationTitle(null, emptyList(), loading = false))
        assertEquals("", conversationTitle(null, emptyList(), loading = true))
    }

    private val torqueOnOff =
        Requirements(enclosure = "weatherproof", service = "on_off", minTorque = TorqueRequirement(500.0, "nm"))

    @Test
    fun `a requirement reads as met, or as what the model has, from what the backend decided`() {
        val short = UnmetRequirement("min_torque_nm", JsonPrimitive(500.0), JsonPrimitive(120))
        val checks = requirementChecks(torqueOnOff, listOf(emptyList(), listOf(short)))
        assertEquals(
            listOf(
                RequirementCheck(
                    "Service: On/off",
                    listOf(CheckResult(CheckState.MET, "Meets"), CheckResult(CheckState.MET, "Meets")),
                ),
                RequirementCheck(
                    "Torque ≥ 500 Nm",
                    listOf(CheckResult(CheckState.MET, "Meets"), CheckResult(CheckState.UNMET, "Has 120")),
                ),
            ),
            checks,
        )
    }

    @Test
    fun `enclosure and supply are never relaxed, so they need no check`() {
        val checks = requirementChecks(Requirements(enclosure = "weatherproof", powerSupply = "110v"), listOf(emptyList()))
        assertTrue(checks.isEmpty())
    }

    @Test
    fun `a model that came from a lookup was never checked, so it does not read as meeting anything`() {
        val checks = requirementChecks(torqueOnOff, listOf(null, emptyList()))
        assertEquals(CheckState.NOT_CHECKED, checks.first().results.first().state)
        assertEquals(CheckState.MET, checks.first().results.last().state)
        assertTrue(requirementChecks(torqueOnOff, listOf(null)).isEmpty())
    }

    @Test
    fun `a miss with no published value says so, and a miss outside the list still appears`() {
        val missing = UnmetRequirement("min_starts_per_hour", JsonPrimitive(100), null)
        val check = requirementChecks(Requirements(), listOf(listOf(missing))).single()
        assertEquals("Starts per hour", check.label)
        assertEquals(CheckResult(CheckState.UNMET, "Not published"), check.results.single())
    }

    @Test
    fun `a search result does not claim to be every model that fits`() {
        assertEquals("Smallest that fit", resultsTitle(listOf("A", "B"), matches = listOf("A", "B")))
        assertEquals("2 models", resultsTitle(listOf("A", "B"), matches = emptyList()))
    }

    @Test
    fun `models from a lookup next to matches are not called a fit`() {
        assertEquals("3 models", resultsTitle(listOf("A", "B", "C"), matches = listOf("B")))
    }

    @Test
    fun `thousands are grouped as the data sheet prints them`() {
        val large = actuator(ratings = listOf(Rating("on_off", 79700, 9000, 6641.7, 40)))
        assertEquals("On/off 79,700 in-lb", summaryLine(large, TorqueDisplay.IN_LB))
        assertEquals("6,641.7", rows(large, unit = TorqueDisplay.FT_LB).first { "Torque" in it.label }.values.single())
        assertEquals(listOf("≥ 1,500.5 in-lb"), requirementChips(Requirements(minTorque = TorqueRequirement(1500.5, "in_lb"))))
    }

    @Test
    fun `the strip hides while it says what the title says, and returns once the requirements change`() {
        val title = "Weatherproof · 220 V · ≥ 500 Nm"
        val named = Requirements(enclosure = "weatherproof", powerSupply = "220v", minTorque = TorqueRequirement(500.0, "nm"))
        assertTrue(stripRepeatsTitle(requirementChips(named), title))
        assertFalse(stripRepeatsTitle(requirementChips(named.copy(minTorque = null)), title))
        assertFalse(stripRepeatsTitle(requirementChips(named), "761A00-11300000/A"))
    }

    @Test
    fun `the requirements line wraps between requirements, never inside one`() {
        assertEquals(
            "220\u00A0V\u00A0single\u00A0phase · ≥\u00A01,500\u00A0in-lb · ≥\u00A0960\u00A0starts/\u2060h",
            requirementsLine(listOf("220 V single phase", "≥ 1,500 in-lb", "≥ 960 starts/h")),
        )
    }

    @Test
    fun `a CSA requirement reads apart from the CSA row of the data sheet`() {
        val checks = requirementChecks(Requirements(csaCertified = true, minCyclesPerHour = 1200), listOf(emptyList()))
        assertEquals(listOf("Cycles per hour ≥ 1,200", "CSA certification"), checks.map { it.label })
        assertTrue("CSA certified" in rows(actuator()).map { it.label })
    }

    @Test
    fun `a part number in a narrow column breaks at its hyphen`() {
        assertEquals(2, partNumberOnTwoLines("761A00-11300000/A").lines().size)
        assertEquals("761A00-", partNumberOnTwoLines("761A00-11300000/A").lines().first())
    }

    @Test
    fun `the view follows how many models there are`() {
        assertEquals(ResultLayout.CARD, resultLayout(1))
        assertEquals(ResultLayout.PAIR, resultLayout(2))
        assertEquals(ResultLayout.LIST, resultLayout(3))
    }

    @Test
    fun `a list row summarizes torque by service, as published`() {
        val two = actuator(ratings = listOf(Rating("on_off", 690, 80, 57.5, 40), Rating("modulating", 550, 65, 45.8, 35)))
        assertEquals("On/off 80 Nm · Modulating 65 Nm", summaryLine(two, TorqueDisplay.NM))
        assertEquals("On/off 690 in-lb", summaryLine(actuator(), TorqueDisplay.IN_LB))
        val threePhase = actuator(ratings = listOf(Rating("unspecified", 690, 80, 57.5, 40)))
        assertEquals("80 Nm, service not stated", summaryLine(threePhase, TorqueDisplay.NM))
        val listed = actuator(ratings = emptyList(), electricals = emptyList(), notAvailable = true)
        assertEquals("N/A in the data sheet", summaryLine(listed, TorqueDisplay.NM))
    }

    @Test
    fun `sections group every row and drop the empty ones`() {
        val sections = specSections(listOf(actuator()), TorqueDisplay.NM, onlyDifferences = false)
        assertEquals(listOf("General", "Torque and duty", "Speed and current", "Operation"), sections.map { it.title })
        assertEquals(rows(actuator()).size, sections.sumOf { it.rows.size })
    }

    @Test
    fun `only differences leaves out the rows and sections all models share`() {
        val sections =
            specSections(listOf(actuator(torqueNm = 80), actuator(torqueNm = 120)), TorqueDisplay.NM, onlyDifferences = true)
        assertEquals(listOf("Torque and duty"), sections.map { it.title })
        assertEquals(listOf("Torque, on/off (Nm)"), sections.single().rows.map { it.label })
    }

    @Test
    fun `a long wait says so`() {
        assertEquals("Checking the data tables", workingText(9))
        assertEquals("Still checking. This can take up to a minute.", workingText(10))
    }
}
