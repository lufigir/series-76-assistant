"""What an uploaded audio file really is, read from its bytes because a client's Content-Type proves nothing."""

WAV = "audio/wav"
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
