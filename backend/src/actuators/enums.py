from enum import StrEnum


class Enclosure(StrEnum):
    WEATHERPROOF = "weatherproof"
    EXPLOSIONPROOF = "explosionproof"


class PowerSupply(StrEnum):
    """The power tables printed in the data sheet."""

    V110_SINGLE_PHASE = "110v_single_phase"
    V220_SINGLE_PHASE = "220v_single_phase"
    V220_THREE_PHASE = "220v_three_phase"
    V24_AC_DC = "24v_ac_dc"
    V24_DC = "24v_dc"


class RequestedSupply(StrEnum):
    """What a user can ask for, which is coarser than the tables: "24V" spans two of them."""

    V110 = "110v"
    V220 = "220v"
    V220_SINGLE_PHASE = "220v_single_phase"
    V220_THREE_PHASE = "220v_three_phase"
    V24 = "24v"
    V24_AC = "24v_ac"
    V24_DC = "24v_dc"


class TableService(StrEnum):
    """The service a whole table is printed for."""

    ON_OFF_AND_MODULATING = "on_off_and_modulating"
    ON_OFF = "on_off"
    MODULATING = "modulating"
    UNSPECIFIED = "unspecified"


class RatingService(StrEnum):
    ON_OFF = "on_off"
    MODULATING = "modulating"
    UNSPECIFIED = "unspecified"


class RequestedService(StrEnum):
    ON_OFF = "on_off"
    MODULATING = "modulating"


class Frequency(StrEnum):
    HZ_60 = "60hz"
    HZ_50 = "50hz"
    NONE = "none"


class RequestedFrequency(StrEnum):
    HZ_60 = "60hz"
    HZ_50 = "50hz"


class TorqueUnit(StrEnum):
    IN_LB = "in_lb"
    NM = "nm"
    FT_LB = "ft_lb"
