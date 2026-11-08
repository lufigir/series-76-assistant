"""The catalog the app uses to suggest and build part numbers."""

from httpx import AsyncClient

from seed import TEST_USERS

EMAIL, PASSWORD = next(iter(TEST_USERS.items()))


async def test_the_catalog_lists_every_part_number_with_its_enclosure_supply_and_top_torque(
    client: AsyncClient,
) -> None:
    token = (await client.post("/api/auth/login", json={"email": EMAIL, "password": PASSWORD})).json()["token"]

    response = await client.get("/api/actuators/part-numbers", headers={"Authorization": f"Bearer {token}"})

    assert response.status_code == 200
    catalog = response.json()
    assert len(catalog) == 68
    assert catalog == sorted(catalog, key=lambda entry: entry["part_number"])
    assert {
        "part_number": "761X30-11300000/A",
        "enclosure": "explosionproof",
        "power_supply": "110v_single_phase",
        "max_torque_nm": 80,
    } in catalog
    # Siblings share size, enclosure and supply, so torque is what tells them apart in the builder.
    assert {
        "part_number": "761B00-11300000/A",
        "enclosure": "weatherproof",
        "power_supply": "110v_single_phase",
        "max_torque_nm": 100,
    } in catalog
    # An N/A row publishes no ratings, which is missing data and not a torque of zero.
    assert {
        "part_number": "761A00-11330C00/A",
        "enclosure": "weatherproof",
        "power_supply": "220v_three_phase",
        "max_torque_nm": None,
    } in catalog


async def test_the_catalog_needs_a_token(client: AsyncClient) -> None:
    assert (await client.get("/api/actuators/part-numbers")).status_code == 401
