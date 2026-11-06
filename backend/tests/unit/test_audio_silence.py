import io
import struct
import wave

from src.transcriptions.audio import M4A, WAV, is_digital_silence


def wav_of(samples: list[int]) -> bytes:
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(16000)
        out.writeframes(b"".join(struct.pack("<h", sample) for sample in samples))
    return buffer.getvalue()


def test_a_wav_of_zeros_is_silence() -> None:
    assert is_digital_silence(wav_of([0] * 16000), WAV)


def test_a_quiet_room_is_left_to_the_model() -> None:
    assert not is_digital_silence(wav_of([30, -30] * 8000), WAV)


def test_an_unreadable_wav_is_left_to_the_model() -> None:
    assert not is_digital_silence(b"RIFF\x24\x00\x00\x00WAVEjunk", WAV)


def test_compressed_audio_is_left_to_the_model() -> None:
    assert not is_digital_silence(b"\x00\x00\x00\x18ftypmp42" + b"\0" * 64, M4A)
