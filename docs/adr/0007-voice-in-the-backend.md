# 7. Transcribe voice in the backend, with Gemini

Status: accepted, November 2026

## Context

The optional voice feature has to get part numbers right, and a part number heard wrong gives a confident answer about the wrong actuator. We compared engines on four phrases, in English and Spanish, spoken by a synthetic Windows voice, with one pass each:

| Engine | Requirements | Part numbers |
|---|---|---|
| `sherpa-onnx` Whisper tiny (98 MB) | Good in English; in Spanish, "o no" for "on/off" | Wrong: `760-13000/A` |
| `sherpa-onnx` Whisper base (152 MB) | Good | Wrong: `761A00-113 000 000-860` |
| Gemini, plain prompt | Good | Exact in Spanish; one zero missing in English |
| Gemini, prompt with the part number format | Good | Exact |

## Decision

The app records and calls `POST /api/transcriptions`. The backend transcribes with the model in `TRANSCRIPTION_MODEL`, discards the audio and returns text, which the user reviews before sending. The model never acts on audio directly.

## Consequences

Voice costs budget and needs a connection, where an on-device engine would not. OpenRouter rejects audio with a 402 below USD 0.50 of credit, and the assessment's account was close to that when measured, so voice may fail during evaluation. The app then says so and leaves the keyboard available.

This sample is small and synthetic. It justifies the choice of engine and says nothing about a noisy plant floor.
