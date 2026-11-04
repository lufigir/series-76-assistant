import pytest

from src.transcriptions.audio import M4A, WAV, sniff_audio_type


@pytest.mark.parametrize(
    ("head", "expected"),
    [
        (b"RIFF\x24\x00\x00\x00WAVE", WAV),
        (b"\x00\x00\x00\x18ftypmp42", M4A),
        (b"\x00\x00\x00\x20ftypisom", M4A),
        (b"RIFF\x24\x00\x00\x00AVI ", None),
        (b"%PDF-1.7\n%abc", None),
        (b"", None),
    ],
)
def test_the_type_comes_from_the_bytes(head: bytes, expected: str | None) -> None:
    assert sniff_audio_type(head) == expected
