"""The transcription endpoint as a client sees it, with a scripted model in place of the provider."""

import asyncio
import io
import re
import wave
from collections.abc import AsyncIterator
from pathlib import Path

import pytest
from fastapi import FastAPI
from httpx import AsyncClient
from langchain_core.messages import BaseMessage

from seed import TEST_USERS
from src.transcriptions.dependencies import MAX_AUDIO_BYTES
from src.transcriptions.service import TRANSCRIPTION_PROMPT
from tests.fakes import Gate, ScriptedChatModel

WAV = (Path(__file__).parents[1] / "fixtures" / "audio" / "en-part.wav").read_bytes()
EMAIL, PASSWORD = next(iter(TEST_USERS.items()))


def compact_part_numbers(text: str) -> list[str]:
    return re.findall(r"76[1-7][A-Z]\d{2}", text)


@pytest.fixture
async def headers(client: AsyncClient) -> dict[str, str]:
    response = await client.post("/api/auth/login", json={"email": EMAIL, "password": PASSWORD})
    return {"Authorization": f"Bearer {response.json()['token']}"}


def audio_block(message: BaseMessage) -> dict[str, str]:
    return next(block for block in message.content if isinstance(block, dict) and block["type"] == "audio")


async def test_a_recording_comes_back_as_text(
    client: AsyncClient, headers: dict[str, str], transcription_model: ScriptedChatModel
) -> None:
    transcription_model.script = ["  What is the torque of 761A00-11300000/A?\n"]
    response = await client.post("/api/transcriptions", content=WAV, headers=headers)
    assert response.status_code == 200
    assert response.json() == {"text": "What is the torque of 761A00-11300000/A?"}
    assert audio_block(transcription_model.calls[-1][0])["mime_type"] == "audio/wav"


async def test_the_declared_type_is_not_believed(
    client: AsyncClient, headers: dict[str, str], transcription_model: ScriptedChatModel
) -> None:
    calls = len(transcription_model.calls)
    response = await client.post(
        "/api/transcriptions", content=b"%PDF-1.7 not audio", headers={**headers, "Content-Type": "audio/wav"}
    )
    assert response.status_code == 415
    assert set(response.json()) == {"status", "message", "data", "trace_id"}
    assert len(transcription_model.calls) == calls


async def test_an_anonymous_caller_is_refused(client: AsyncClient) -> None:
    assert (await client.post("/api/transcriptions", content=WAV)).status_code == 401


async def test_a_recording_over_the_limit_is_refused(client: AsyncClient, headers: dict[str, str]) -> None:
    response = await client.post("/api/transcriptions", content=WAV + b"\0" * MAX_AUDIO_BYTES, headers=headers)
    assert response.status_code == 413


async def test_the_limit_holds_without_a_content_length(client: AsyncClient, headers: dict[str, str]) -> None:
    async def chunks() -> AsyncIterator[bytes]:
        yield WAV
        yield b"\0" * MAX_AUDIO_BYTES

    response = await client.post("/api/transcriptions", content=chunks(), headers=headers)
    assert response.status_code == 413


@pytest.mark.parametrize("reply", ["  ", "NO_SPEECH", " NO_SPEECH\n"])
async def test_silence_is_an_error_the_user_can_act_on(
    client: AsyncClient, headers: dict[str, str], transcription_model: ScriptedChatModel, reply: str
) -> None:
    transcription_model.script = [reply]
    assert (await client.post("/api/transcriptions", content=WAV, headers=headers)).status_code == 422


async def test_digital_silence_never_reaches_the_model(
    client: AsyncClient, headers: dict[str, str], transcription_model: ScriptedChatModel
) -> None:
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(16000)
        out.writeframes(b"\0" * 32000)
    silent = buffer.getvalue()
    calls = len(transcription_model.calls)
    assert (await client.post("/api/transcriptions", content=silent, headers=headers)).status_code == 422
    assert len(transcription_model.calls) == calls


async def test_a_provider_failure_says_voice_is_unavailable(
    client: AsyncClient, headers: dict[str, str], transcription_model: ScriptedChatModel
) -> None:
    transcription_model.script = [RuntimeError("402 from the provider")]
    response = await client.post("/api/transcriptions", content=WAV, headers=headers)
    assert response.status_code == 503
    assert "402" not in response.json()["message"]


async def test_no_connection_is_held_during_the_model_call(
    app: FastAPI, client: AsyncClient, headers: dict[str, str], transcription_model: ScriptedChatModel
) -> None:
    gate = Gate()
    transcription_model.script = [gate, "Text."]
    request = asyncio.create_task(client.post("/api/transcriptions", content=WAV, headers=headers))
    await gate.entered.wait()
    assert app.state.engine.pool.checkedout() == 0
    gate.release.set()
    assert (await request).status_code == 200


def test_the_prompt_holds_no_part_number_the_model_could_repeat_from_silence() -> None:
    assert not compact_part_numbers(TRANSCRIPTION_PROMPT)
