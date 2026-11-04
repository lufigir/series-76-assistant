from typing import Any

from fastapi import APIRouter, Depends

from src.auth.dependencies import get_user_without_connection
from src.transcriptions.dependencies import AudioDep, TranscriptionServiceDep
from src.transcriptions.schemas import TranscriptionResponse

router = APIRouter(prefix="/api", tags=["transcriptions"])

_BINARY = {"schema": {"type": "string", "format": "binary"}}
_AUDIO_BODY: dict[str, Any] = {
    "requestBody": {"required": True, "content": {"audio/wav": _BINARY, "audio/mp4": _BINARY}},
}


@router.post(
    "/transcriptions",
    # A dependency on the route runs first, so an anonymous caller is refused before their upload is read.
    dependencies=[Depends(get_user_without_connection)],
    openapi_extra=_AUDIO_BODY,
)
async def transcribe(audio: AudioDep, service: TranscriptionServiceDep) -> TranscriptionResponse:
    return TranscriptionResponse(text=await service.transcribe(audio.content, audio.mime_type))
