"""The catalog the app uses to suggest and build part numbers."""

from httpx import AsyncClient

from seed import TEST_USERS

EMAIL, PASSWORD = next(iter(TEST_USERS.items()))


async def test_the_catalog_lists_every_part_number_with_its_enclosure_and_supply(client: AsyncClient) -> None:
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
    } in catalog


async def test_the_catalog_needs_a_token(client: AsyncClient) -> None:
    assert (await client.get("/api/actuators/part-numbers")).status_code == 401
