"""The conversation endpoints as a client sees them, with a scripted model and real PostgreSQL."""

import asyncio
import json
import uuid
from collections.abc import AsyncIterator
from typing import Any

import pytest
from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient, Response

from seed import TEST_USERS
from src.agent.tools import TOOL_NAMES
from src.main import create_app
from tests.fakes import Gate, ScriptedChatModel, call

EMAIL, PASSWORD = next(iter(TEST_USERS.items()))
OTHER_EMAIL, OTHER_PASSWORD = list(TEST_USERS.items())[1]
PART = "761A00-11300000/A"
RIGHT = "It delivers 700 in-lb in on/off service."


async def _headers(client: AsyncClient, email: str = EMAIL, password: str = PASSWORD) -> dict[str, str]:
    response = await client.post("/api/auth/login", json={"email": email, "password": password})
    return {"Authorization": f"Bearer {response.json()['token']}"}


@pytest.fixture
async def headers(client: AsyncClient) -> dict[str, str]:
    return await _headers(client)


@pytest.fixture
async def other_headers(client: AsyncClient) -> dict[str, str]:
    return await _headers(client, OTHER_EMAIL, OTHER_PASSWORD)


async def ask(client: AsyncClient, headers: dict[str, str], query: str, session_id: str | None = None) -> Response:
    payload: dict[str, Any] = {"query": query}
    if session_id:
        payload["session_id"] = session_id
    return await client.post("/api/conversation", json=payload, headers=headers)


def seen_text(model: ScriptedChatModel) -> str:
    return " ".join(str(message.content) for message in model.calls[-1])


async def test_a_new_conversation_returns_its_session_and_cards(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = [call("get_actuator", part_number="761a00 11300000"), RIGHT]
    response = await ask(client, headers, "Torque of 761a00 11300000?")
    assert response.status_code == 200
    body = response.json()
    assert uuid.UUID(body["session_id"])
    assert body["answer"] == RIGHT
    assert body["outcome"] == "answered"
    assert [product["part_number"] for product in body["products"]] == [PART]
    on_off = next(r for r in body["products"][0]["ratings"] if r["service"] == "on_off")
    assert (on_off["torque_in_lb"], on_off["torque_nm"], on_off["torque_ft_lb"]) == (700, 80, 58.3)


async def test_the_chat_response_never_carries_tool_names(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = [call("get_actuator", part_number=PART), RIGHT]
    body = (await ask(client, headers, "Torque?")).text
    history = await client.get(f"/api/sessions/{json.loads(body)['session_id']}/messages", headers=headers)
    for name in TOOL_NAMES:
        assert name not in body
        assert name not in history.text


async def test_a_follow_up_reaches_the_model_with_the_earlier_turn(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = [call("get_actuator", part_number=PART), RIGHT]
    first = (await ask(client, headers, f"Torque of {PART}?")).json()

    chat_model.script = [call("get_actuator", part_number=PART), "It draws 15 W."]
    second = await ask(client, headers, "And how much power does it draw?", first["session_id"])
    assert second.status_code == 200
    assert second.json()["session_id"] == first["session_id"]
    assert f"Torque of {PART}?" in seen_text(chat_model)
    assert RIGHT in seen_text(chat_model)


async def test_conversations_survive_a_restart(
    app: FastAPI, client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = [
        call("find_actuators", changes={"enclosure": "explosionproof", "power_supply": "110v"}),
        "What torque do you need?",
    ]
    first = (await ask(client, headers, "Explosionproof at 110 V")).json()

    restarted_model = ScriptedChatModel(
        script=[call("find_actuators", changes={"min_torque": {"value": 500, "unit": "nm"}}), "This one fits."]
    )
    restarted = create_app(app.state.settings, chat_model=restarted_model, transcription_model=restarted_model)
    async with restarted.router.lifespan_context(restarted):
        transport = ASGITransport(app=restarted, raise_app_exceptions=False)
        async with AsyncClient(transport=transport, base_url="http://test") as restarted_client:
            response = await ask(restarted_client, headers, "At least 500 Nm", first["session_id"])
            history = await restarted_client.get(f"/api/sessions/{first['session_id']}/messages", headers=headers)

    body = response.json()
    assert response.status_code == 200
    assert body["requirements"]["enclosure"] == "explosionproof"
    assert body["requirements"]["power_supply"] == "110v"
    assert body["requirements"]["min_torque"] == {"value": 500, "unit": "nm"}
    assert body["outcome"] == "recommendation"
    assert body["products"][0]["part_number"] == "764X30-11300000/A"
    assert "Explosionproof at 110 V" in seen_text(restarted_model)
    assert [message["role"] for message in history.json()["items"]] == ["user", "assistant", "user", "assistant"]


async def test_an_invented_number_is_retried_and_never_reaches_memory(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = [call("get_actuator", part_number=PART), "It delivers 5000 Nm.", RIGHT]
    body = (await ask(client, headers, "Torque?")).json()
    assert body["answer"] == RIGHT
    assert "5000" in seen_text(chat_model)
    debug = (await client.get(f"/api/messages/{body['message_id']}/debug", headers=headers)).json()
    assert debug["trace"]["validation"] == {"rejected": ["5000"], "retried": True, "used_template": False}
    assert len(debug["trace"]["model_calls"]) == 3

    chat_model.script = ["Anything else?"]
    await ask(client, headers, "Thanks", body["session_id"])
    assert "5000" not in seen_text(chat_model)


async def test_two_invented_answers_end_in_the_template(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = [call("get_actuator", part_number=PART), "5000 Nm.", "6000 Nm."]
    body = (await ask(client, headers, "Torque?")).json()
    assert "700 in-lb" in body["answer"]
    assert "5000" not in body["answer"]
    assert "6000" not in body["answer"]
    assert body["outcome"] == "answered"
    debug = (await client.get(f"/api/messages/{body['message_id']}/debug", headers=headers)).json()
    assert debug["trace"]["validation"]["used_template"] is True


async def test_a_number_recalled_from_an_earlier_turn_needs_a_new_lookup(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = [call("get_actuator", part_number=PART), RIGHT]
    first = (await ask(client, headers, "Torque?")).json()

    chat_model.script = ["It draws 15 W.", call("get_actuator", part_number=PART), "It draws 15 W."]
    second = (await ask(client, headers, "And power?", first["session_id"])).json()
    assert second["answer"] == "It draws 15 W."
    assert [product["part_number"] for product in second["products"]] == [PART]


async def test_an_answer_naming_a_tool_is_rejected(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = [call("get_actuator", part_number=PART), "get_actuator says 700 in-lb.", RIGHT]
    assert (await ask(client, headers, "Torque?")).json()["answer"] == RIGHT


@pytest.mark.parametrize(
    ("query", "outcome", "products", "suggestions"),
    [
        ("761A00-11380000/A", "invalid_part_number", 0, True),
        ("761A00-11330C00/A", "missing_data", 1, False),
        ("761A", "ambiguous", 0, True),
        ("ABC-123", "invalid_part_number", 0, False),
    ],
)
async def test_edge_cases_of_a_lookup_have_their_own_outcome(
    client: AsyncClient,
    headers: dict[str, str],
    chat_model: ScriptedChatModel,
    query: str,
    outcome: str,
    products: int,
    suggestions: bool,
) -> None:
    chat_model.script = [call("get_actuator", part_number=query), "Here is what I found."]
    body = (await ask(client, headers, query)).json()
    assert body["outcome"] == outcome
    assert len(body["products"]) == products
    assert bool(body["suggestions"]) is suggestions


async def test_a_requirement_nothing_meets_returns_labelled_alternatives(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = [
        call(
            "find_actuators",
            changes={"enclosure": "weatherproof", "power_supply": "110v", "min_torque": {"value": 9000, "unit": "nm"}},
        ),
        "No model meets that.",
    ]
    body = (await ask(client, headers, "Weatherproof, 110 V, 9000 Nm")).json()
    assert body["outcome"] == "no_match"
    assert body["products"] == []
    assert body["alternatives"]
    assert body["alternatives"][0]["unmet"][0]["requirement"] == "min_torque_nm"


async def test_clear_and_reset_change_the_kept_requirements(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = [call("find_actuators", changes={"enclosure": "explosionproof", "service": "on_off"}), "Ok."]
    first = (await ask(client, headers, "Explosionproof on/off")).json()

    chat_model.script = [call("find_actuators", clear=["service"]), "Ok."]
    second = (await ask(client, headers, "Forget the service", first["session_id"])).json()
    assert second["requirements"]["service"] is None
    assert second["requirements"]["enclosure"] == "explosionproof"

    chat_model.script = [call("find_actuators", changes={"service": "modulating"}, reset=True), "Ok."]
    third = (await ask(client, headers, "New application: modulating", first["session_id"])).json()
    assert third["requirements"]["enclosure"] is None
    assert third["requirements"]["service"] == "modulating"


async def test_a_bad_tool_argument_goes_back_to_the_model_and_is_recorded(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = [
        call("find_actuators", changes={"enclosure": "waterproof"}),
        "Which enclosure do you need?",
    ]
    response = await ask(client, headers, "Waterproof")
    assert response.status_code == 200
    assert response.json()["requirements"]["enclosure"] is None
    assert "enclosure" in str(chat_model.calls[-1][-1].content)


async def test_a_looping_model_is_stopped_and_still_gets_an_answer(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.repeat = call("get_actuator", part_number=PART)
    response = await ask(client, headers, "Torque?")
    chat_model.repeat = None
    assert response.status_code == 200
    body = response.json()
    assert "700 in-lb" in body["answer"]
    debug = (await client.get(f"/api/messages/{body['message_id']}/debug", headers=headers)).json()
    assert debug["trace"]["validation"]["used_template"] is True
    assert len(debug["trace"]["model_calls"]) <= 6
    assert len(debug["trace"]["tools"]) <= 8


async def test_debug_shows_tools_parameters_and_data(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = [call("get_actuator", part_number=PART), RIGHT]
    body = (await ask(client, headers, "Torque?")).json()
    trace = (await client.get(f"/api/messages/{body['message_id']}/debug", headers=headers)).json()["trace"]
    assert trace["tools"][0]["name"] == "get_actuator"
    assert trace["tools"][0]["parameters"] == {"part_number": PART}
    assert trace["tools"][0]["result"]["actuator"]["part_number"] == PART
    assert trace["model"]
    assert trace["prompt_version"]
    assert trace["latency_ms"] >= 0
    assert set(trace) >= {"input_tokens", "output_tokens", "cost_usd", "model_calls"}


async def test_data_sheet_notes_are_available_to_the_agent(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = [call("get_data_sheet_notes"), "The motor voltage tolerance is 10%, per IEC 60034-1."]
    body = (await ask(client, headers, "Voltage tolerance?")).json()
    assert body["outcome"] == "answered"
    assert body["answer"].startswith("The motor voltage tolerance is 10%")


async def test_a_second_turn_on_a_busy_conversation_gets_409(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = ["Hello."]
    session_id = (await ask(client, headers, "Hi")).json()["session_id"]

    gate = Gate()
    chat_model.script = [gate, "First."]
    first = asyncio.create_task(ask(client, headers, "One", session_id))
    await gate.entered.wait()
    second = await ask(client, headers, "Two", session_id)
    gate.release.set()
    assert second.status_code == 409
    assert (await first).status_code == 200

    chat_model.script = ["Free again."]
    assert (await ask(client, headers, "Three", session_id)).status_code == 200


async def test_a_failing_provider_answers_503_and_the_conversation_goes_on(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = ["Hello."]
    session_id = (await ask(client, headers, "Hi")).json()["session_id"]

    chat_model.script = [RuntimeError("secret provider detail")]
    failed = await ask(client, headers, "Fail", session_id)
    assert failed.status_code == 503
    assert "secret" not in failed.text
    assert set(failed.json()) == {"status", "message", "data", "trace_id"}

    chat_model.script = ["Back."]
    assert (await ask(client, headers, "Again", session_id)).json()["answer"] == "Back."
    history = await client.get(f"/api/sessions/{session_id}/messages", headers=headers)
    assert [message["content"] for message in history.json()["items"]] == ["Hi", "Hello.", "Again", "Back."]


async def test_a_turn_failing_after_a_tool_call_does_not_break_the_next_one(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = ["Hello."]
    session_id = (await ask(client, headers, "Hi")).json()["session_id"]
    chat_model.script = [call("get_actuator", part_number=PART), RuntimeError("provider down")]
    assert (await ask(client, headers, "Torque?", session_id)).status_code == 503

    chat_model.script = [call("get_actuator", part_number=PART), RIGHT]
    assert (await ask(client, headers, "Torque again?", session_id)).status_code == 200


async def test_a_failed_first_turn_leaves_no_conversation(
    client: AsyncClient, other_headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    before = (await client.get("/api/sessions", headers=other_headers)).json()
    chat_model.script = [RuntimeError("provider down")]
    assert (await ask(client, other_headers, "Hi")).status_code == 503
    assert (await client.get("/api/sessions", headers=other_headers)).json() == before


@pytest.fixture
async def slow_app(app: FastAPI, chat_model: ScriptedChatModel) -> AsyncIterator[FastAPI]:
    settings = app.state.settings.model_copy(update={"turn_timeout_seconds": 1})
    application = create_app(settings, chat_model=chat_model, transcription_model=chat_model)
    async with application.router.lifespan_context(application):
        yield application


async def test_a_turn_past_its_deadline_answers_504_and_frees_the_conversation(
    client: AsyncClient, headers: dict[str, str], slow_app: FastAPI, chat_model: ScriptedChatModel
) -> None:
    # Only the blocked turn runs under the one-second deadline, because a healthy turn can outlast it on a busy machine.
    chat_model.script = ["Hello."]
    session_id = (await ask(client, headers, "Hi")).json()["session_id"]

    transport = ASGITransport(app=slow_app, raise_app_exceptions=False)
    async with AsyncClient(transport=transport, base_url="http://test") as slow_client:
        chat_model.script = [Gate(), "Too late."]
        assert (await ask(slow_client, headers, "Slow", session_id)).status_code == 504

    chat_model.script = ["Fine."]
    assert (await ask(client, headers, "Again", session_id)).status_code == 200


async def test_a_recommendation_names_its_matches_and_asks_for_the_next_criterion(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = [
        call("find_actuators", changes={"enclosure": "weatherproof", "power_supply": "110v"}),
        "Several models fit.",
    ]

    body = (await ask(client, headers, "Weatherproof at 110 V")).json()

    assert body["matches"] == [product["part_number"] for product in body["products"]]
    assert body["follow_ups"] == ["On/off", "Modulating"]
    history = await client.get(f"/api/sessions/{body['session_id']}/messages", headers=headers)
    assert history.json()["items"][-1]["follow_ups"] == ["On/off", "Modulating"]
    assert history.json()["items"][-1]["matches"] == body["matches"]


async def test_a_lookup_is_a_card_but_not_a_match(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = [call("get_actuator", part_number=PART), RIGHT]

    body = (await ask(client, headers, f"Torque of {PART}?")).json()

    assert [product["part_number"] for product in body["products"]] == [PART]
    assert body["matches"] == []
    assert body["follow_ups"] == []


async def test_an_answer_about_a_named_part_number_needs_its_lookup(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = ["It is weatherproof.", call("get_actuator", part_number=PART), RIGHT]

    body = (await ask(client, headers, f"What enclosure does {PART} have?")).json()

    assert body["answer"] == RIGHT
    assert [product["part_number"] for product in body["products"]] == [PART]


async def test_a_pronoun_passed_as_a_part_number_asks_back(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = [call("get_actuator", part_number="it"), "Which part number do you mean?"]
    assert (await ask(client, headers, "What is its torque?")).json()["outcome"] == "ambiguous"


async def test_the_outcome_of_a_turn_without_data_follows_the_declaration_and_not_the_wording(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = ["Please provide the part number."]
    assert (await ask(client, headers, "What is its torque?")).json()["outcome"] == "ambiguous"

    chat_model.script = [call("mark_out_of_scope"), "I only cover Series 76 actuators. Anything else?"]
    assert (await ask(client, headers, "Capital of France?")).json()["outcome"] == "out_of_scope"


async def test_sessions_list_only_your_own_most_recent_first(
    client: AsyncClient,
    headers: dict[str, str],
    other_headers: dict[str, str],
    chat_model: ScriptedChatModel,
) -> None:
    chat_model.script = ["One."]
    first = (await ask(client, headers, "First conversation")).json()["session_id"]
    chat_model.script = ["Two."]
    second = (await ask(client, headers, "Second conversation")).json()["session_id"]

    sessions = (await client.get("/api/sessions", headers=headers)).json()["items"]
    assert [session["id"] for session in sessions[:2]] == [second, first]
    assert set(sessions[0]) == {"id", "title", "date", "preview"}
    assert sessions[0]["title"] == "Second conversation"

    others = (await client.get("/api/sessions", headers=other_headers)).json()["items"]
    assert not {first, second} & {session["id"] for session in others}

    chat_model.script = ["Three."]
    await ask(client, headers, "More", first)
    assert (await client.get("/api/sessions", headers=headers)).json()["items"][0]["id"] == first


async def test_a_session_previews_its_latest_answer(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = ["Which part number?"]
    session_id = (await ask(client, headers, "Torque?")).json()["session_id"]
    chat_model.script = [call("get_actuator", part_number=PART), RIGHT]
    await ask(client, headers, f"Torque of {PART}?", session_id)

    session = (await client.get("/api/sessions", headers=headers)).json()["items"][0]

    assert session["id"] == session_id
    assert session["preview"] == RIGHT


async def test_the_first_turn_that_finds_a_model_names_the_conversation_for_good(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = ["Hello! Which actuator?"]
    first = (await ask(client, headers, "hola")).json()
    assert first["title"] == "hola"

    chat_model.script = [call("get_actuator", part_number=PART), RIGHT]
    second = (await ask(client, headers, "and its torque?", first["session_id"])).json()
    assert second["title"] == PART

    chat_model.script = [call("find_actuators", changes={"enclosure": "explosionproof"}), "These fit."]
    third = (await ask(client, headers, "explosionproof ones?", first["session_id"])).json()
    assert third["title"] == PART

    session = (await client.get("/api/sessions", headers=headers)).json()["items"][0]
    assert session["title"] == PART


async def test_similar_models_come_as_cards_after_the_one_named(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = [call("find_similar", part_number="767C00-11320000/A"), "The 767B00-11320000/A is closest."]
    body = (await ask(client, headers, "another one like the 767C00-11320000/A?")).json()

    assert body["outcome"] == "answered"
    assert [product["part_number"] for product in body["products"]] == [
        "767C00-11320000/A",
        "767B00-11320000/A",
        "767A00-11320000/A",
        "766B00-11320000/A",
    ]


async def test_someone_elses_conversation_does_not_exist_for_you(
    client: AsyncClient,
    headers: dict[str, str],
    other_headers: dict[str, str],
    chat_model: ScriptedChatModel,
) -> None:
    chat_model.script = [call("get_actuator", part_number=PART), RIGHT]
    body = (await ask(client, headers, "Mine")).json()
    session_id, message_id = body["session_id"], body["message_id"]

    calls_before = len(chat_model.calls)
    assert (await ask(client, other_headers, "Theirs", session_id)).status_code == 404
    assert len(chat_model.calls) == calls_before
    assert (await client.get(f"/api/sessions/{session_id}/messages", headers=other_headers)).status_code == 404
    assert (await client.get(f"/api/messages/{message_id}/debug", headers=other_headers)).status_code == 404


async def test_an_unknown_session_is_404_and_a_malformed_one_is_422(
    client: AsyncClient, headers: dict[str, str]
) -> None:
    assert (await ask(client, headers, "Hi", str(uuid.uuid4()))).status_code == 404
    assert (await ask(client, headers, "Hi", "not-a-uuid")).status_code == 422


async def test_a_user_message_has_no_debug(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel
) -> None:
    chat_model.script = ["Hello."]
    session_id = (await ask(client, headers, "Hi")).json()["session_id"]
    history = (await client.get(f"/api/sessions/{session_id}/messages", headers=headers)).json()
    assert (await client.get(f"/api/messages/{history['items'][0]['id']}/debug", headers=headers)).status_code == 404


@pytest.mark.parametrize("query", ["", "   ", "x" * 2001])
async def test_an_unusable_query_is_rejected_before_any_model_call(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel, query: str
) -> None:
    calls_before = len(chat_model.calls)
    response = await ask(client, headers, query)
    assert response.status_code == 422
    assert len(chat_model.calls) == calls_before
    assert query.strip() == "" or query not in response.text


@pytest.mark.parametrize(
    ("method", "path"),
    [
        ("POST", "/api/conversation"),
        ("GET", "/api/sessions"),
        ("GET", f"/api/sessions/{uuid.uuid4()}/messages"),
        ("GET", f"/api/messages/{uuid.uuid4()}/debug"),
    ],
)
async def test_every_route_needs_a_token(client: AsyncClient, method: str, path: str) -> None:
    response = await client.request(method, path, json={"query": "hi"} if method == "POST" else None)
    assert response.status_code == 401


async def test_sessions_page_by_cursor_without_skipping_or_repeating(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr("src.conversations.repository.SESSIONS_PAGE", 2)
    created = []
    for index in range(5):
        chat_model.script = [f"Answer {index}."]
        created.append((await ask(client, headers, f"Conversation {index}")).json()["session_id"])

    seen: list[str] = []
    cursor = None
    while True:
        params = {"cursor": cursor} if cursor else {}
        page = (await client.get("/api/sessions", headers=headers, params=params)).json()
        assert len(page["items"]) <= 2
        seen += [session["id"] for session in page["items"]]
        cursor = page["next_cursor"]
        if cursor is None:
            break

    assert [session_id for session_id in seen if session_id in created] == created[::-1]
    assert len(seen) == len(set(seen))


async def test_history_opens_on_the_latest_messages_and_reaches_back(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr("src.conversations.repository.MESSAGES_PAGE", 3)
    chat_model.script = ["One."]
    session_id = (await ask(client, headers, "First")).json()["session_id"]
    chat_model.script = ["Two."]
    await ask(client, headers, "Second", session_id)
    chat_model.script = ["Three."]
    await ask(client, headers, "Third", session_id)

    latest = (await client.get(f"/api/sessions/{session_id}/messages", headers=headers)).json()
    assert [message["content"] for message in latest["items"]] == ["Two.", "Third", "Three."]
    older = (
        await client.get(
            f"/api/sessions/{session_id}/messages", headers=headers, params={"cursor": latest["next_cursor"]}
        )
    ).json()
    assert [message["content"] for message in older["items"]] == ["First", "One.", "Second"]
    assert older["next_cursor"] is None


@pytest.mark.parametrize("path", ["/api/sessions", "/api/sessions/{session_id}/messages"])
async def test_a_cursor_the_api_did_not_issue_is_422(
    client: AsyncClient, headers: dict[str, str], chat_model: ScriptedChatModel, path: str
) -> None:
    chat_model.script = ["One."]
    session_id = (await ask(client, headers, "First")).json()["session_id"]
    response = await client.get(path.format(session_id=session_id), headers=headers, params={"cursor": "%%%"})
    assert response.status_code == 422
