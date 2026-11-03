"""What the data sheet and the summary say in prose. They are images and paragraphs, so ingest.py does not read them."""

DATA_SHEET_NOTES = {
    "definitions": {
        "full_load_current_fla": "Current in amps at maximum rated load.",
        "locked_rotor_current_lra": "Current in amps at locked rotor load, drawn at startup.",
        "duty_cycle": "Percentage of time the actuator can operate over a cycle without overheating, class S4.",
        "operating_speed": "Seconds to complete a full 90-degree turn. AC models vary with 50 Hz or 60 Hz power.",
        "motor_power": "Rating of the internal motor, in watts.",
        "on_off_service": "The actuator operates in two positions: fully open or fully closed.",
        "modulating_service": "The actuator can stop at any point of its 90-degree rotation for precise flow control.",
    },
    "tolerances": {
        "motor_voltage": "+/- 10%",
        "power_frequency": "+/- 5%",
    },
    "ratings_basis": {
        "duty_cycle": "Per IEC 60034-1, for on/off and modulating service.",
        "starts_and_cycles_per_hour": "Based on rated torque.",
    },
    "certification": {
        "weatherproof": "Table titled CSA, CE and UKCA. Sizes 6 and 7 are not CSA certified.",
        "explosionproof": "Table titled CSA.",
        "explosionproof_hazardous_location": [
            "Class I, Division 1, Groups C, D; T4",
            "Ex d IIB T4 Gb",
            "Class I, Zone 1, AEx d IIB T4 Gb",
        ],
    },
}
