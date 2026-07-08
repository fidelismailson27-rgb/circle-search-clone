# Circle Search Clone

A native Android app that lets you circle anything on any screen — triggered from a
third-party launcher (e.g. Square Home) or gesture app (e.g. Ubikitouch) — and get an
AI answer without ever leaving the app you were using. There is no proprietary
backend: **you bring your own AI endpoint** (BYOK — Bring Your Own Key), and the app
talks to it directly.

This project was built spec-first with [GitHub Spec Kit](https://github.com/github/spec-kit) —
see [`specs/001-visual-search-assistant/`](specs/001-visual-search-assistant/) for the
full specification, implementation plan, research decisions, data model, and task
breakdown, and [`.specify/memory/constitution.md`](.specify/memory/constitution.md)
for the project's non-negotiable architecture principles.

## How it works

1. An external trigger (a launcher shortcut or gesture binding) invokes the app's one
   public interface — see
   [`contracts/trigger-intent-contract.md`](specs/001-visual-search-assistant/contracts/trigger-intent-contract.md).
2. A transparent selection overlay appears instantly over whatever app you're
   currently in — no screenshot is taken yet.
3. You draw a selection (lasso or rectangle) around what you want to ask about.
4. Only once you confirm the selection does the app request screen-capture consent,
   capture a single frame, crop it to your selection, compress it, and send it to
   your configured AI endpoint.
5. The answer appears in a chat panel over your current app. You can ask follow-up
   questions in the same panel — that conversation exists only in memory and is
   discarded the moment you close the panel.

## BYOK setup

Circle Search has no built-in AI provider. Open the app (its own launcher icon, not
the hidden trigger) and go to Settings:

1. Tap **+** to add an AI Endpoint Profile:
   - **Name** — anything memorable, e.g. "OpenAI" or "Home Ollama".
   - **Base URL** — the OpenAI-compatible base URL of your provider, e.g.
     `https://api.openai.com/v1` or `http://192.168.1.50:11434/v1` for a local
     Ollama instance on your home network.
   - **Model** — the exact model name your provider expects (must support image
     input for the initial circle-and-ask turn).
   - **API Key** — leave blank if your endpoint doesn't require one.
2. Tap **Test** to confirm the connection works.
3. Select the profile you want active (the radio button). Any other profiles can be
   arranged into a fallback order — if the active profile's request fails (network
   error, timeout, HTTP error, or a malformed response), the app automatically
   retries the next profile in that order and discreetly shows which one actually
   answered.

Any OpenAI-compatible `/v1/chat/completions` endpoint works: OpenAI, Google Gemini
(via its OpenAI-compatibility layer), OpenRouter, Ollama, LM Studio, or any other
self-hosted server speaking the same wire format — the app uses a single client code
path for all of them, with no per-provider special-casing.

**Local network endpoints**: a `http://` Base URL is only permitted when it resolves
to a loopback/private/link-local address (e.g. `192.168.x.x`, `10.x.x.x`,
`localhost`) — this is validated when you save the profile. Public endpoints must use
`https://`.

## Permissions, and why the app needs them

The app asks for exactly three things, each explained on first run before the
corresponding system prompt appears:

| Permission | Why |
|---|---|
| **Display over other apps** (`SYSTEM_ALERT_WINDOW`) | Draws the selection overlay and the result panel directly on top of whatever app you're using, so you never have to switch away from it. |
| **Accessibility service** | Used only as a fallback: if a screen blocks normal screen capture (e.g. some banking or streaming apps set `FLAG_SECURE`), the app reads the on-screen text instead — on demand only, never continuously, and never logged. |
| **Screen capture** | Requested fresh by Android's system dialog every single time you circle something — this is a hard platform requirement for non-system apps on modern Android (see `research.md` R1) and cannot be granted once for all future searches. |

The app never captures your screen, reads on-screen content, or sends anything
anywhere except in direct response to you triggering a search.

## Architecture

Kotlin + 100% Jetpack Compose, MVVM in `data` / `domain` / `ui` layers, Hilt for
dependency injection, Retrofit + OkHttp for networking, Coil for image loading. Full
details and the reasoning behind every non-obvious decision (the `MediaProjection`
consent lifecycle, the fallback/session-pinning design, the image-resend token-economy
tradeoff, etc.) are in [`specs/001-visual-search-assistant/research.md`](specs/001-visual-search-assistant/research.md).

## Building

```bash
./gradlew assembleDebug
```

CI ([`.github/workflows/build.yml`](.github/workflows/build.yml)) builds the debug
APK and runs the unit test suite on every push to `main`; the resulting APK is
published as a workflow artifact.

## Project status

Implemented: the full core flow (trigger → overlay → capture → AI answer), BYOK
multi-profile configuration with automatic fallback, ephemeral follow-up chat,
first-run permission onboarding, and `FLAG_SECURE` text-extraction fallback. See
[`specs/001-visual-search-assistant/tasks.md`](specs/001-visual-search-assistant/tasks.md)
for the full task breakdown, including what's still open (manual on-device validation
across the API 26–35 range, and further test coverage).
