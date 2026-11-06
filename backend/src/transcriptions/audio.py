"""What an uploaded audio file really is, read from its bytes because a client's Content-Type proves nothing."""

import array
import io
import wave

WAV = "audio/wav"
# About -72 dBFS on 16-bit audio. A real microphone's noise floor sits above it, so only digital silence falls below.
_SILENCE_PEAK = 8
# OpenRouter reads the format from this subtype, and it names the container m4a, not mp4.
M4A = "audio/m4a"


def sniff_audio_type(head: bytes) -> str | None:
    """Return the MIME type of a supported container, or None.

    A video in an MP4 container also passes, since the brand does not separate them reliably across recorders.
    The provider rejects it, and the size limit has already bounded what that costs.
    """
    if head[:4] == b"RIFF" and head[8:12] == b"WAVE":
        return WAV
    if head[4:8] == b"ftyp":
        return M4A
    return None


def is_digital_silence(audio: bytes, mime_type: str) -> bool:
    """True for a 16-bit PCM WAV whose every sample is near zero.

    Given pure digital silence, the transcription model answered with an invented part number, so this never
    reaches it. Compressed audio would need a decoder, and anything this cannot read is left to the model.
    """
    if mime_type != WAV:
        return False
    try:
        with wave.open(io.BytesIO(audio)) as recording:
            if recording.getsampwidth() != 2:
                return False
            samples = array.array("h", recording.readframes(recording.getnframes()))
    except (wave.Error, EOFError):
        return False
    return max(map(abs, samples), default=0) < _SILENCE_PEAK
