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

Real silence exposed a trap in the format hint. With example part numbers in the prompt, Gemini answered pure silence with those same numbers, and a recording on the emulator with nobody speaking produced a valid-looking `761A00-11300000/A`. The prompt now describes the format in words and asks for `NO_SPEECH`. Five silent recordings and one of noise then came back as no speech, and the four speech samples kept their part numbers. A test keeps part numbers out of the prompt.

The app records M4A at 64 kbps. At 32 kbps one Spanish sample came back with a wrong letter in its part number, and at 64 kbps that sample came back right. Both results are single passes.

This sample is small and synthetic. It justifies the choice of engine and says nothing about a noisy plant floor.

## Update: a different Gemini, and silence kept away from it

A wider pass in November 2026 ran 8 phrases and 3 recordings without speech, three times each, in WAV and M4A, on seven audio models through OpenRouter. `gemini-3.5-flash-lite` dropped a zero from `11320000` in 8 of 24 WAV runs and 5 of 24 M4A runs, the error this decision exists to avoid. `gemini-3.1-flash-lite` got all 24 speech runs exact in both formats, and its one failure was pure digital silence, which it answered with an invented part number. Quiet room noise and white noise both came back as no speech.

So `gemini-3.1-flash-lite` is the default, and digital silence never reaches it. The app reads the recorder's peak amplitude and drops a recording that never left 0. The backend checks a WAV for near-zero samples and answers 422 without a model call. M4A from another client still reaches the model, since checking it would need an audio decoder in the backend.
