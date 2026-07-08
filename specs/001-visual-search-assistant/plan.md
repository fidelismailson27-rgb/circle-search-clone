# Implementation Plan: Visual Search Assistant

**Branch**: `001-visual-search-assistant` | **Date**: 2026-07-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-visual-search-assistant/spec.md`

## Summary

A native Android app that lets a user circle anything on any screen — triggered
externally by a third-party launcher/gesture app — and get an AI answer without ever
leaving the app they were using. There is no proprietary backend: the user brings their
own OpenAI-compatible AI endpoint(s), configured as named profiles with automatic
fallback. The result panel is a lightweight, in-memory-only chat that lets the user
follow up before discarding everything on close.

The technical approach follows the constitution exactly: Kotlin + Compose, MVVM in
`data`/`domain`/`ui` layers, Hilt, Retrofit/OkHttp, on-demand `MediaProjection` capture
of exactly one frame per search, and a single OpenAI-compatible network client with no
per-provider code paths. Four open design questions carried over from the spec are
resolved explicitly below and in [research.md](./research.md): the Android 14/15
`MediaProjection` + foreground-service lifecycle, how chat context survives a
mid-conversation endpoint fallback, the image-resend strategy across chat turns, and
how a single network path serves every BYOK provider.

## Technical Context

**Language/Version**: Kotlin (2.x line, JVM target 17), 100% Jetpack Compose UI — no
other UI toolkit, per constitution I.

**Primary Dependencies**: Jetpack Compose (BOM), Hilt (DI), Retrofit + OkHttp
(networking), Coil (image loading, used for local previews/thumbnails — not for the AI
request path, which sends raw compressed bytes directly), Kotlin Coroutines/Flow,
Jetpack DataStore + `androidx.security.crypto` (Keystore-backed encryption) for BYOK
credential storage, `kotlinx.serialization` for DTOs. Exact dependency **versions** are
intentionally not pinned in this plan (see research.md "Supporting decisions") — they
must be resolved to their current stable releases compatible with `targetSdk 35` as a
first implementation task, to avoid this document asserting version numbers that may
already be stale.

**Storage**: No database. Only `AiEndpointProfile` records (name, base URL, encrypted
API key, model name, active flag, fallback order) and `SearchPreferences` (text
fallback toggle, compression quality) are persisted, via encrypted DataStore. Nothing
else is persisted: `VisualSearchRequest`, `ConversationSession`, and `ChatMessage` data
are in-memory only for the lifetime of the result panel (constitution + spec
Assumptions — no search history).

**Testing**: JUnit + MockK for `domain`/`data` unit tests (use cases, repositories,
request-building/fallback logic); Turbine for `Flow`/`StateFlow` assertions in
ViewModels; OkHttp `MockWebServer` for the network client's request-shape and
SSE-streaming-parsing tests (this doubles as the closest thing to a "contract test"
available here, since the real contract is an external BYOK endpoint the app doesn't
own); Compose UI tests (`createComposeRule`) for the overlay, settings, and result-panel
composables; a manual instrumentation checklist (`quickstart.md`) for the parts that
fundamentally require a real device and real system consent dialogs (MediaProjection,
AccessibilityService, SYSTEM_ALERT_WINDOW), which cannot be meaningfully faked in a
unit test.

**Target Platform**: Android phones, tablets, and foldables; `minSdk 26` (Android 8.0)
– `targetSdk 35` (Android 15).

**Project Type**: Single native Android app module (not a client/server split — the
"backend" is whatever external BYOK endpoint the user configures, which this app never
owns or deploys).

**Performance Goals**: Selection overlay visible within ~1s of trigger (SC-001, made
achievable specifically by decoupling overlay display from `MediaProjection` — see Key
Design Decision 1); single-frame capture-to-teardown cycle on the order of hundreds of
milliseconds once consent is granted; zero measurable continuous background activity
between searches (SC-006).

**Constraints**: No continuous/polling capture (constitution III). Foreground service
must declare and start with `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` before any
`MediaProjection` API call, and must register `MediaProjection.Callback` before
`createVirtualDisplay()` (verified platform requirement, research.md R1). At most one
full-resolution `Bitmap` alive at a time; aggressive `recycle()` (constitution VII).
Images downscaled + WebP ~q80 before transmission (constitution VI/VIII). Cleartext
HTTP restricted to private IP ranges via `networkSecurityConfig` (constitution IX).
Single OpenAI-compatible client, no per-provider branches (constitution IX,
research.md R4).

**Scale/Scope**: Single user per device install. Five screens/surfaces: onboarding,
Settings (profile list + editor + fallback ordering), the transparent selection
overlay, the result/chat `ModalBottomSheet`, and the externally-invokable trigger entry
point (no visible UI of its own). No multi-account, no server-side component.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design below.*

| Constitution principle | Gate | Status |
|---|---|---|
| I. Kotlin & Compose-Only Stack | Kotlin, Compose, Hilt, Retrofit/OkHttp, Coil, Compose-native animation only — no web tech anywhere | **PASS** — reflected throughout Technical Context and Project Structure |
| II. Clean Architecture & SOLID | `data`/`domain`/`ui` layers, explicit types, no unjustified `!!`, no silent error swallowing | **PASS** — Project Structure below enforces the three layers; error modeling covered in data-model.md (`VisualSearchRequest.status`, aggregated fallback errors) |
| III. On-Demand Screen Capture Only | Single frame per trigger, immediate `VirtualDisplay` teardown, no polling | **PASS** — research.md R1 sequence captures exactly one frame and tears down immediately; strengthened further by deferring capture until after selection confirm |
| IV. Accessibility as Lightweight Trigger & Fallback | Minimal event/flag subscription, text-extraction fallback only | **PASS** — `TriggerAccessibilityService` scoped to trigger reception + on-demand `AccessibilityNodeInfo` text extraction; no continuous scraping |
| V. Never Fail Silently | Black-frame detection → fallback; all network/parse failures surfaced | **PASS** — research.md "black/blank frame detection" + R4 fallback/aggregated-error design; FR-016/019/020/024 all map to explicit UI states |
| VI. Overlay Capture & Precise Coordinate Mapping | `TYPE_APPLICATION_OVERLAY`, `Path`-based lasso, crop on `Dispatchers.Default`, density/multi-window-aware mapping | **PASS** — research.md "coordinate mapping" decision; overlay is transparent-over-live-screen (Key Design Decision 1) |
| VII. Bitmap & Memory Discipline | Explicit `recycle()`, no retained full-res frame after crop, ≤1 full-res frame alive | **PASS** — capture pipeline in `data/capture` releases the source `Bitmap` immediately after producing the downscaled/cropped WebP payload |
| VIII. Compression & Transfer Efficiency | Downscale + WebP ~q80, multipart preferred, Base64 only if required | **PASS** — compression step precedes network layer; R4's `image_url` data-URI form is the Base64 fallback used because OpenAI-compatible chat-completions is a JSON-body endpoint (no multipart variant across all target providers), which is the one deliberate, documented exception constitution VI anticipates ("Base64 apenas se o endpoint exigir JSON inline") |
| IX. BYOK Networking Model | No proprietary backend; single OpenAI-compatible client; encrypted key storage; private-IP cleartext only | **PASS** — research.md R4 is a direct, literal implementation of this principle; storage per "Supporting decisions" |
| X. Foreground Service & Result UX Contract | `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` declared + active projection; `MediaProjection.Callback`; `ModalBottomSheet` with loading/success/error; cancel + memory release on dismiss | **PASS** — research.md R1 (verified against official docs) and R2/R3 chat design; FR-007/FR-029 cover dismiss-time cleanup |

No violations requiring Complexity Tracking justification.

## Key Design Decisions (explicitly resolved open questions)

These four points were left open by the spec and are resolved here per your request;
full rationale and alternatives-considered are in [research.md](./research.md).

### 1. `MediaProjection` + `ForegroundService` lifecycle on Android 14/15

Verified against official Android documentation (developer.android.com/media/grow/
media-projection, fetched during this planning phase): **a `MediaProjection` token may
be used to call `createVirtualDisplay()` exactly once** — calling it a second time on
the same instance, or reusing a `createScreenCaptureIntent()` result across multiple
`getMediaProjection()` calls, throws `SecurityException` on Android 14+. There is no
way to keep a capture session "warm" across multiple triggers.

**Design response**: decouple the *visible overlay* from *capture*. The selection
overlay is a transparent `TYPE_APPLICATION_OVERLAY` window shown instantly on trigger,
using only the once-granted `SYSTEM_ALERT_WINDOW` permission — the live screen remains
visible through it while the user draws. Only after the user **confirms** a selection
does the app run the full per-capture cycle:

1. Launch a translucent `MediaProjectionConsentActivity` to host the consent flow
   (`createScreenCaptureIntent()` requires an Activity context) — shows the system
   "Start recording or casting?" dialog, every single time, with no way around it for a
   non-privileged third-party app.
2. `ScreenCaptureForegroundService` calls
   `startForeground(id, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)`
   **before** touching any `MediaProjection` API.
3. `getMediaProjection(...)` → `registerCallback(...)` (mandatory, else
   `IllegalStateException`) → exactly one `createVirtualDisplay(...)` sized to current
   display metrics.
4. On the single captured frame (bounded by a timeout): release `VirtualDisplay`, close
   `ImageReader`, call `mediaProjection.stop()` explicitly, stop the service.
5. `MediaProjection.Callback.onStop()` performs idempotent cleanup regardless of
   whether it fired from step 4 or an external revocation (status-bar chip, screen
   lock) — the projection is always treated as permanently dead once stopped.

This keeps SC-001 (overlay in <1s) achievable despite the consent dialog being
unavoidable per search, by making the dialog part of "submitting" a selection rather
than a blocker on seeing the overlay at all.

### 2. Chat context when fallback switches profiles mid-conversation

The `ConversationSession.messages` list is a single, profile-agnostic flat list owned
by the result panel's `ViewModel`. On a mid-conversation fallback, the **full existing
history is replayed verbatim** to the next candidate profile (safe because the wire
format is identical across profiles — Key Design Decision 4). The profile that first
succeeds becomes "session-pinned" for the rest of that conversation only (avoiding
repeated retries of a known-bad profile), without altering the user's persisted active
profile in Settings. A brand-new visual search always starts fresh from the real
configured active profile. See research.md R2 for the full rationale.

### 3. Image resend strategy across chat turns (optimized for token economy)

**Chosen strategy**: attach the compressed selection image to a profile's request only
the first time that specific profile is used within the session (initial turn, and
once more if a fallback introduces a profile that hasn't seen the image yet). All other
turns are text-only. This is the most token-economical option that request explicitly
asked for, because vision models typically charge a large, fixed token cost per image
regardless of the accompanying text — paying that cost on every turn would dominate the
cost of what are usually short follow-up questions, and most natural follow-ups are
answerable from the already-generated first-turn description already present in the
resent text history.

**Tradeoff, stated plainly**: because the endpoint is stateless (the whole message
array is resent from scratch every call — constitution IX), a turn that omits the image
means the model literally cannot see new visual detail not already covered by its own
earlier answer. This is accepted as the default per the token-economy goal, but is
implemented as one small, isolated `ImageAttachmentPolicy` function specifically so it
can be swapped for "always attach" later without touching the rest of the chat
pipeline. Full detail in research.md R3.

### 4. Single OpenAI-compatible path, no per-provider adapters

One Retrofit interface, one DTO set, one interceptor chain. Per-profile Base URL is
supplied per-call via a dynamic `@Url` parameter (not one Retrofit instance per
profile); the API key is injected by a single `Interceptor` reading a per-request tag
(not a static global header), because one conversation can span multiple profiles
across its turns. Streaming vs. non-streaming responses are distinguished once, at the
transport layer, by response `Content-Type` — never by a per-provider branch. This is a
literal implementation of constitution IX and is detailed in research.md R4.

## Project Structure

### Documentation (this feature)

```text
specs/001-visual-search-assistant/
├── plan.md              # This file
├── research.md           # Phase 0 output — decisions, rationale, alternatives
├── data-model.md         # Phase 1 output — entities, fields, relationships, lifecycle
├── quickstart.md         # Phase 1 output — manual end-to-end validation guide
└── tasks.md              # Phase 2 output (/speckit-tasks — not created by this plan)
```

No `contracts/` directory: this app has no API of its own to document a machine-
readable contract for (it is a client of an external, user-owned endpoint whose
contract is the OpenAI chat-completions shape already fully specified in research.md
R4 and data-model.md). The one true "interface" this app exposes to the *outside* world
— the externally-invokable trigger entry point launchers/gesture apps call — is
documented below in Project Structure and will get an explicit `Intent`/Activity
contract captured as part of the relevant implementation task in `tasks.md`.

### Source Code (repository root)

Single Android application module, `app/`, internally split into the constitution's
mandated `data` / `domain` / `ui` layers:

```text
app/
├── build.gradle.kts
├── src/
│   ├── main/
│   │   ├── AndroidManifest.xml
│   │   ├── kotlin/com/circulesearch/app/
│   │   │   ├── CircleSearchApplication.kt        # @HiltAndroidApp
│   │   │   │
│   │   │   ├── data/
│   │   │   │   ├── network/
│   │   │   │   │   ├── OpenAiCompatibleApi.kt     # single Retrofit interface (research.md R4)
│   │   │   │   │   ├── dto/                       # ChatCompletionRequest/Response/Chunk, ChatMessageDto
│   │   │   │   │   ├── ProfileAuthInterceptor.kt  # per-request tag → Authorization header
│   │   │   │   │   ├── SseChatStreamParser.kt      # streaming vs. plain-JSON branch
│   │   │   │   │   ├── ImageAttachmentPolicy.kt    # research.md R3 strategy, isolated/swappable
│   │   │   │   │   └── NetworkModule.kt            # Hilt bindings (OkHttpClient, Retrofit)
│   │   │   │   ├── capture/
│   │   │   │   │   ├── ScreenCaptureForegroundService.kt
│   │   │   │   │   ├── MediaProjectionConsentActivity.kt
│   │   │   │   │   ├── SingleFrameCapturer.kt      # VirtualDisplay+ImageReader, one-shot (R1)
│   │   │   │   │   └── BlackFrameDetector.kt       # FLAG_SECURE heuristic
│   │   │   │   ├── accessibility/
│   │   │   │   │   ├── TriggerAccessibilityService.kt
│   │   │   │   │   └── TextExtractionFallback.kt
│   │   │   │   ├── settings/
│   │   │   │   │   ├── EndpointProfileLocalDataSource.kt   # encrypted DataStore
│   │   │   │   │   └── SearchPreferencesLocalDataSource.kt
│   │   │   │   └── repository/
│   │   │   │       ├── EndpointProfileRepositoryImpl.kt
│   │   │   │       ├── VisualSearchRepositoryImpl.kt       # owns fallback + streaming orchestration
│   │   │   │       └── PermissionStatusRepositoryImpl.kt
│   │   │   │
│   │   │   ├── domain/
│   │   │   │   ├── model/            # AiEndpointProfile, SearchPreferences, VisualSearchRequest,
│   │   │   │   │                     # ConversationSession, ChatMessage, PermissionStatus
│   │   │   │   ├── repository/       # interfaces only
│   │   │   │   └── usecase/          # StartVisualSearchUseCase, SendFollowUpMessageUseCase,
│   │   │   │                         # TestEndpointConnectionUseCase, SaveEndpointProfileUseCase,
│   │   │   │                         # ReorderFallbackUseCase, CheckRequiredPermissionsUseCase
│   │   │   │
│   │   │   ├── ui/
│   │   │   │   ├── trigger/          # TriggerEntryActivity — exported, no visible UI
│   │   │   │   ├── overlay/          # SelectionOverlayWindow, SelectionCanvas, CoordinateMapper
│   │   │   │   ├── result/           # ResultBottomSheet, ChatMessageList, ChatInputBar, ResultViewModel
│   │   │   │   ├── settings/         # SettingsScreen, ProfileEditorScreen, FallbackOrderScreen
│   │   │   │   ├── onboarding/       # OnboardingScreen, PermissionExplainerCard
│   │   │   │   └── theme/
│   │   │   │
│   │   │   └── di/                   # AppModule, DispatchersModule (beyond NetworkModule above)
│   │   │
│   │   └── res/
│   │       ├── xml/network_security_config.xml   # private-IP-only cleartext (constitution IX)
│   │       ├── xml/accessibility_service_config.xml
│   │       └── values/strings.xml
│   │
│   ├── test/kotlin/...        # JUnit + MockK + Turbine + MockWebServer (domain/data unit tests)
│   └── androidTest/kotlin/... # Compose UI tests
│
└── proguard-rules.pro
```

**Structure Decision**: single-module Android app (no multi-module split) — the app's
scope (five screens/surfaces, one external interface) does not yet justify the
build-time and navigation-boilerplate cost of feature modules. `data`/`domain`/`ui`
package separation delivers the constitution's layering requirement without it.
Revisit modularization only if build times or team size later make it worthwhile
(constitution's Clean Architecture principle is about dependency direction, not module
count).

## Complexity Tracking

*No entries — the Constitution Check above has no unresolved violations.*
