"""The voice set: English phrases spoken by Windows' synthetic voices, heard through the real TranscriptionService.

`--generate` writes the recordings into tests/fixtures/audio/voice with Windows' System.Speech, so it only runs on
Windows; the recordings are committed, so anyone can run the set. Without it, each recording goes to the configured
TRANSCRIPTION_MODEL and passes when every part number comes back exact and every expected word is there. It spends
that provider's budget, about USD 0.0003 a recording, so it stays out of CI.

Usage: uv run python -m tests.evals.voice [--generate] [--repeat 3]
"""

import argparse
import asyncio
import subprocess
from dataclasses import dataclass
from pathlib import Path

from src.core.config import Settings
from src.core.errors import AppError
from src.core.llm import create_transcription_model
from src.transcriptions.service import TranscriptionService

FOLDER = Path(__file__).parents[1] / "fixtures" / "audio" / "voice"
VOICES = ("Microsoft David Desktop", "Microsoft Zira Desktop")


@dataclass(frozen=True)
class VoiceCase:
    id: str
    spoken: str
    part_numbers: tuple[str, ...] = ()
    words: tuple[str, ...] = ()


# Part numbers are spoken the way people read them off a label, character by character, and in a few cases the way a
# speech engine reads the printed form, which says "eleven million" for 11000000.
CASES = (
    VoiceCase(
        "torque-by-part-number",
        "What is the torque of seven six one A zero zero, dash, one one three zero zero zero zero zero, slash A?",
        ("761A00-11300000/A",),
        ("torque",),
    ),
    VoiceCase("printed-form", "What is the torque of 761A00-11300000/A?", ("761A00-11300000/A",), ("torque",)),
    VoiceCase(
        "explosionproof-letter",
        "I need the seven six four X three zero, dash, one one three seven zero zero zero zero, slash A.",
        ("764X30-11370000/A",),
    ),
    VoiceCase(
        "letter-inside-the-code",
        "Tell me about seven six one A zero zero, dash, one one three three C zero zero, slash A.",
        ("761A00-11330C00/A",),
    ),
    VoiceCase(
        "certification",
        "Is the seven six seven C zero zero, dash, one one three two zero zero zero zero, slash A, CSA certified?",
        ("767C00-11320000/A",),
        ("CSA",),
    ),
    VoiceCase(
        "current-at-a-frequency",
        "What is the locked rotor current of seven six four B zero zero, dash, one one three two zero zero zero zero, "
        "slash A, at fifty hertz?",
        ("764B00-11320000/A",),
        ("50",),
    ),
    VoiceCase(
        "two-part-numbers",
        "Compare seven six five Y three zero, dash, one one three two zero zero zero zero, slash A, and seven six five "
        "X three zero, dash, one one three two zero zero zero zero, slash A.",
        ("765Y30-11320000/A", "765X30-11320000/A"),
    ),
    VoiceCase(
        "requirements-in-newton-meters",
        "Explosionproof, twenty four volts DC, modulating, at least three hundred newton meters.",
        (),
        ("explosion", "24", "DC", "modulating", "300"),
    ),
    VoiceCase(
        "requirements-in-inch-pounds",
        "Weatherproof, two hundred twenty volts single phase, at least two thousand inch pounds.",
        (),
        ("weatherproof", "220", "single", "2000"),
    ),
    VoiceCase(
        "similar-model",
        "Something like the seven six six B zero zero, dash, one one three two zero zero zero zero, slash A, but "
        "smaller.",
        ("766B00-11320000/A",),
        ("smaller",),
    ),
    VoiceCase("partial-follow-up", "And the seven six two?", (), ("762",)),
    VoiceCase("closing", "Thanks, that's all.", (), ("thank",)),
)


def generate() -> None:
    FOLDER.mkdir(parents=True, exist_ok=True)
    for index, case in enumerate(CASES):
        voice = VOICES[index % len(VOICES)]
        target = FOLDER / f"{case.id}.wav"
        spoken = case.spoken.replace("'", "''")
        # 16 kHz mono keeps a recording near 100 KB, and it is what a phone microphone sends anyway.
        script = (
            "Add-Type -AssemblyName System.Speech; "
            "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
            f"$s.SelectVoice('{voice}'); "
            "$f = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000, "
            "[System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono); "
            f"$s.SetOutputToWaveFile('{target}', $f); $s.Speak('{spoken}'); $s.Dispose()"
        )
        subprocess.run(["powershell.exe", "-NoProfile", "-Command", script], check=True)
        print(f"wrote {target.name} ({voice})")


def score(case: VoiceCase, text: str) -> list[str]:
    failures = [f"missing {part}" for part in case.part_numbers if part not in text]
    failures += [f"missing word {word}" for word in case.words if word.lower() not in text.lower()]
    return failures


async def run(repeat: int) -> None:
    settings = Settings()  # type: ignore[call-arg]
    service = TranscriptionService(create_transcription_model(settings), settings.llm_timeout_seconds)
    passed = total = 0
    for case in CASES:
        audio = (FOLDER / f"{case.id}.wav").read_bytes()
        for _ in range(repeat):
            total += 1
            try:
                text = await service.transcribe(audio, "audio/wav")
            except AppError as error:
                print(f"FAIL {case.id}: {error.status} {error.message}")
                continue
            failures = score(case, text)
            passed += not failures
            print(f"{'ok  ' if not failures else 'FAIL'} {case.id}: {text!r} {'; '.join(failures)}")
    print(f"\nModel `{settings.transcription_model}`: {passed} of {total} passed.")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--generate", action="store_true")
    parser.add_argument("--repeat", type=int, default=1)
    args = parser.parse_args()
    if args.generate:
        generate()
    else:
        asyncio.run(run(args.repeat))


if __name__ == "__main__":
    main()
