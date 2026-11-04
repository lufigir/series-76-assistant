from dataclasses import dataclass
from typing import Annotated

from fastapi import Depends, Request

from src.core.errors import AppError
from src.transcriptions.audio import sniff_audio_type
from src.transcriptions.service import TranscriptionService

# Base64 for the provider makes it a third larger. WAV at 16 kHz fits over two minutes; compressed audio far more.
MAX_AUDIO_BYTES = 5 * 1024 * 1024
_TOO_LARGE = "The recording is over the 5 MB limit"
_SNIFF_BYTES = 12


@dataclass(frozen=True)
class AudioUpload:
    content: bytes
    mime_type: str


async def read_audio(request: Request) -> AudioUpload:
    """Read the raw body, stopping at the limit, since Content-Length is only what the client claims.

    A raw body instead of multipart, whose parser spools large files to disk: audio is never stored.
    """
    declared = request.headers.get("content-length", "")
    if declared.isdigit() and int(declared) > MAX_AUDIO_BYTES:
        raise AppError(413, _TOO_LARGE)
    content = bytearray()
    async for chunk in request.stream():
        content += chunk
        if len(content) > MAX_AUDIO_BYTES:
            raise AppError(413, _TOO_LARGE)
    mime_type = sniff_audio_type(bytes(content[:_SNIFF_BYTES]))
    if mime_type is None:
        raise AppError(415, "The recording must be WAV or M4A audio")
    return AudioUpload(bytes(content), mime_type)


def get_transcription_service(request: Request) -> TranscriptionService:
    return TranscriptionService(request.app.state.transcription_model, request.app.state.settings.turn_timeout_seconds)


AudioDep = Annotated[AudioUpload, Depends(read_audio)]
TranscriptionServiceDep = Annotated[TranscriptionService, Depends(get_transcription_service)]
