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
        assertEquals(listOf("Explosionproof", "24 V", "≥ 1000 Nm"), chips)
        assertTrue(requirementChips(Requirements()).isEmpty())
        assertNull(requirementChips(Requirements()).firstOrNull())
    }

    @Test
    fun `key figures read as published, with the service and frequency of each`() {
        val figures = keyFigures(actuator(), TorqueDisplay.IN_LB)
        assertEquals(
            listOf(
                KeyFigure("Torque, on/off", "690", "in-lb"),
                KeyFigure("Duty, on/off", "40", "%"),
                KeyFigure("Speed at 60 Hz", "20", "s"),
                KeyFigure("FLA at 60 Hz", "1.2", "A"),
            ),
            figures,
        )
    }

    @Test
    fun `key figures hold every service and frequency, so a follow-up on any of them finds its number`() {
        val figures =
            keyFigures(
                actuator(
                    ratings = listOf(Rating("on_off", 690, 80, 57.5, 40), Rating("modulating", 550, 65, 45.8, 40)),
                    electricals = listOf(Electrical("60hz", 20, 1.2, 1.4), Electrical("50hz", 23, 1.1, 1.3)),
                ),
                TorqueDisplay.NM,
            ).associate { it.label to it.value }
        assertEquals("65", figures["Torque, modulating"])
        assertEquals("23", figures["Speed at 50 Hz"])
        assertEquals("1.1", figures["FLA at 50 Hz"])
    }

    @Test
    fun `a collapsed comparison keeps the 50 Hz rows`() {
        assertTrue(isKeyRow("Speed at 50 Hz (s)"))
        assertTrue(isKeyRow("FLA at 50 Hz (A)"))
        assertFalse(isKeyRow("LRA at 50 Hz (A)"))
    }

    @Test
    fun `a three phase row says its service is not stated`() {
        val threePhase = actuator(ratings = listOf(Rating("unspecified", 690, 80, 57.5, 40)))
        assertEquals("Torque, service not stated", keyFigures(threePhase, TorqueDisplay.NM).first().label)
    }

    @Test
    fun `a model without data has no key figures to show`() {
        val listed = actuator(ratings = emptyList(), electricals = emptyList(), notAvailable = true)
        assertTrue(keyFigures(listed, TorqueDisplay.NM).isEmpty())
    }

    @Test
    fun `outcomes read in plain words and declining is not shown as an error`() {
        assertEquals("No exact match", outcomeLabel("no_match"))
        assertEquals(OutcomeTone.CAUTION, outcomeTone("no_match"))
        assertEquals(OutcomeTone.NEGATIVE, outcomeTone("invalid_part_number"))
        assertEquals(OutcomeTone.NEUTRAL, outcomeTone("out_of_scope"))
    }
}
