# Model comparison

## Agent

Every candidate ran the same 36 cases twice on prompt `a1717b6787de` with `temperature=0`, through OpenRouter except Gemini, which ran on Google's API. Cost is the OpenRouter list price times the tokens measured, per turn.

| Model | Passed | Ended in the template | Latency | Tokens in / out | USD per turn | Report |
|---|---:|---:|---:|---:|---:|---|
| `deepseek/deepseek-v4.1-flash` (default) | 72 of 72 | 0 | 2.9 s | 5,267 / 177 | 0.0003 | [report](deepseek-v4.1-flash.md) |
| `gemini-3.5-flash-lite`, on Google's free tier | 72 of 72 | 0 | 3.2 s | 3,688 / 72 | 0.0006 on OpenRouter | [report](gemini-3.5-flash-lite.md) |
| `qwen/qwen3.7-flash` | 68 of 72 | 3 | 7.3 s | 5,425 / 521 | 0.0002 | [report](qwen3.7-flash.md) |
| `deepseek/deepseek-v4-flash` | 67 of 72 | 0 | 4.8 s | 5,347 / 211 | 0.0005 | [report](deepseek-v4-flash.md) |
| `openai/gpt-6-luna`, `openai/gpt-5-nano` | none ran | | | | | |

The OpenAI pair accepts no `temperature` on any OpenRouter provider. With `require_parameters` set, OpenRouter finds no endpoint and every turn fails with `NotFoundResponseError`.

The rule: drop a model that cannot call the tools or that gets a catalog number wrong, then keep the cheapest one that passes at least 71 of 72 with no turn ending in the template. `qwen3.7-flash` costs less per turn, but it missed 4 cases, 3 turns fell back to the template and it took 7.3 s.

`gemini-3.5-flash-lite` ties on accuracy and stays the development model, since Google's free tier costs nothing. Paid on OpenRouter it costs about twice as much per turn as `deepseek-v4.1-flash`, which is why it is not the default.

## Voice

Eight phrases with part numbers and requirements, in English and Spanish, spoken by Windows' synthetic voices, plus pure digital silence, near silence and white noise. Each model heard all 11 recordings three times, through the backend's own `TranscriptionService`. A speech run passes when every part number comes back exact and the requirements survive; a silent one passes when it ends in 422.

| Model | WAV | M4A | Main failure |
|---|---:|---:|---|
| `google/gemini-3.1-flash-lite` (default) | 30 of 33 | 30 of 33 | Invents a part number for pure digital silence, which the app and backend now keep from it |
| `google/gemini-3.5-flash-lite` | 25 of 33 | 28 of 33 | Drops a zero: `765Y30-1132000/A` |
| `google/gemini-2.5-flash-lite` | 21 of 33 | | Mangles part numbers: `7604x30-…` |
| `xiaomi/mimo-v2.6-flash` | 20 of 33 | | Writes numbers as words; invents one for silence |
| `qwen/qwen3.8-omni-flash` | 19 of 33 | | Same, at 5 s a recording |
| `mistralai/voxtral-small-24b-2507`, `openai/gpt-audio-mini` | 15 of 33 | | Refuses or mangles part numbers |

A recording costs under USD 0.0003 on any of them, so accuracy decided.

## Ingestion

Each vision model transcribed the four PDF pages, and every value was compared with the 1,410 in `data/processed/actuators.json`, which a person reviewed.

| Model | Values wrong |
|---|---:|
| `anthropic/claude-haiku-5.5` (extractor) | 0 |
| `google/gemini-3.1-flash-lite` (verifier) | 10 |
| `google/gemini-3.5-flash-lite` | 19 |
| `qwen/qwen3.7-flash` | 27, mostly `I` read for `1` |
| `qwen/qwen3.8-flash` | 57 |
| `z-ai/glm-5.3-flash`, `google/gemini-2.5-flash-lite`, `mistralai/mistral-small-3.2-24b-instruct` | 181 to 504 |
| `openai/gpt-4.1-nano`, `inclusionai/ling-3.0-flash-vl` | did not finish |

Ingestion runs once for about USD 0.05 and `--saved` replays it for free, so a cheaper extractor would only add values for a person to review.
