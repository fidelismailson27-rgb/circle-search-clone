<!--
Sync Impact Report
Version change: [TEMPLATE] → 1.0.0
Modified principles: n/a (initial ratification)
Added sections:
  - Core Principles I–X
  - Technology Stack & Permissions
  - Development Workflow & Quality Gates
  - Governance
Removed sections: none (template placeholders replaced)
Templates requiring updates:
  ✅ .specify/templates/plan-template.md — generic Constitution Check gate, compatible as-is
  ✅ .specify/templates/spec-template.md — generic, no stack leakage, compatible as-is
  ✅ .specify/templates/tasks-template.md — generic task categorization, compatible as-is
  ✅ .claude/skills/speckit-constitution/SKILL.md — no agent-specific references requiring change
Follow-up TODOs: none
-->

# Circle Search Clone Constitution

## Core Principles

### I. Kotlin & Compose-Only Stack (NON-NEGOTIABLE)
The app MUST be written in Kotlin using Coroutines/Flow for all asynchronous and
reactive work. `minSdk` is fixed at 26 and `targetSdk` at 35 (Android 15). All Gradle
build files MUST use Kotlin DSL (`.kts`). All UI MUST be built with Jetpack Compose;
React, Tailwind, Framer Motion, WebView-based UI, or any other web technology are
PROHIBITED anywhere in the app. All animation MUST use native Compose APIs
(`animate*AsState`, `AnimatedVisibility`, `updateTransition`, `Transition`) — no
third-party animation libraries. Dependency injection MUST use Hilt. Networking MUST
use Retrofit + OkHttp. Image loading MUST use Coil.
**Rationale**: A single, coherent native stack keeps the app small, fast, and
maintainable, and guarantees it can integrate tightly with system-level Android APIs
(MediaProjection, AccessibilityService, WindowManager) that have no meaningful web
equivalent.

### II. Clean Architecture & SOLID (NON-NEGOTIABLE)
The codebase MUST be organized in three layers — `data`, `domain`, `ui` — with
dependencies pointing inward (`ui` → `domain` ← `data`). Each class MUST have a single
clear responsibility (SOLID). All public APIs MUST use explicit types; the `!!`
non-null assertion operator MUST NOT be used except where a prior explicit null-check
in the same scope makes non-nullity provably guaranteed and is documented with a
comment explaining why. Errors MUST be modeled explicitly (sealed results / typed
exceptions) and MUST NOT be swallowed — every catch block either recovers, surfaces the
error to the UI, or logs it with enough context to act on it.
**Rationale**: This is a system-integration-heavy app (overlays, services, IPC with an
external HTTP endpoint); silent failures or tangled layers are disproportionately
costly to debug in this domain.

### III. On-Demand Screen Capture Only (NON-NEGOTIABLE)
Screen capture via `MediaProjection` MUST be initiated only in direct response to an
explicit trigger (exported entry point, shortcut, or accessibility gesture). The
capture flow MUST: create the `VirtualDisplay` + `ImageReader`, acquire exactly one
frame, then immediately tear down the `VirtualDisplay` and release the `ImageReader`.
Continuous or polling-based capture is PROHIBITED. The `MediaProjection` session itself
MUST be stopped as soon as the single frame has been acquired unless another capture is
imminently expected in the same user interaction.
**Rationale**: Continuous capture drains battery and raises privacy concerns; a
single-shot model matches the "Circle to Search" trigger-driven UX and is the only
approach acceptable for a background-capable utility app.

### IV. Accessibility Service as Lightweight Trigger & Fallback
The `AccessibilityService` MUST be used only for (a) receiving the external trigger
signal and (b) extracting on-screen text via `AccessibilityNodeInfo` as a fallback when
image-based capture fails. It MUST subscribe to the minimum set of event types and
flags required for these two purposes — broad event subscription (e.g. all window
content changes) is PROHIBITED. The service MUST NOT perform continuous screen
scraping or logging of user content.
**Rationale**: `AccessibilityService` has system-wide performance and privacy
implications; scope must be minimized to what the trigger and fallback strictly need.

### V. Never Fail Silently
Any condition that would silently degrade the user experience MUST be detected and
surfaced. In particular: a captured frame that is black/empty (typically caused by
`FLAG_SECURE` on the foreground app) MUST be detected via pixel/content heuristics and
MUST trigger an automatic fallback to the AccessibilityService text-extraction path,
with a visible notice to the user explaining that image capture was blocked. Network
failures, timeouts, and malformed AI responses MUST be shown in the result UI with a
retry affordance — they MUST NOT be dropped or only logged.
**Rationale**: `FLAG_SECURE` and flaky BYOK endpoints (self-hosted/local models) are
expected, common conditions for this app, not edge cases — the UX must treat them as
first-class states.

### VI. Overlay Capture & Precise Coordinate Mapping
The selection overlay MUST be implemented with `WindowManager` using
`TYPE_APPLICATION_OVERLAY`, drawing the lasso/rectangle selection with `Path` in
Compose. Cropping the captured `Bitmap` to the selection's bounding box MUST run on
`Dispatchers.Default`, never on the main thread. All gesture coordinates MUST be
mapped to source `Bitmap` pixel coordinates accounting for display density, the actual
`VirtualDisplay` resolution, and multi-window/foldable/variable-DPI configurations —
a fixed density or resolution assumption is PROHIBITED.
**Rationale**: Overlay coordinates and capture-buffer pixels live in different
coordinate spaces; on modern Android (foldables, split-screen, per-app density) a naive
1:1 mapping produces visibly wrong crops.

### VII. Bitmap & Memory Discipline
Every `Bitmap` MUST be explicitly `recycle()`d as soon as it is no longer needed. The
full-resolution captured frame MUST NOT be retained in memory after the crop is
produced. Images MUST be downscaled before any network transmission. No more than one
full-resolution frame may be alive in memory at any point in time.
**Rationale**: Repeated high-resolution captures are a direct OOM risk on
memory-constrained devices; capture is triggered by the user unpredictably and must
never accumulate state across invocations.

### VIII. Compression & Transfer Efficiency
Before being sent to an AI endpoint, the cropped image MUST be downscaled and
compressed as WebP at approximately quality 80. The request MUST use multipart binary
upload whenever the configured endpoint supports it; Base64 JSON-inline encoding MUST
be used only when the endpoint requires it (e.g. certain OpenAI-compatible
chat-completions image inputs).
**Rationale**: BYOK endpoints may be on metered connections, local networks, or
resource-constrained self-hosted servers — payload size directly affects latency and
cost for the user.

### IX. BYOK Networking Model (NON-NEGOTIABLE)
There is NO proprietary backend. The app MUST talk directly to an endpoint the user
configures in Settings: Base URL, API Key, and model name. All AI requests MUST go
through a single OpenAI-compatible network client targeting `/v1/chat/completions`
(or equivalent), designed to work against OpenAI, Gemini (via its OpenAI-compatible
layer), OpenRouter, Ollama, and arbitrary local/self-hosted endpoints without
provider-specific client code paths. The API key MUST be stored using
`EncryptedSharedPreferences` or DataStore protected by the Android Keystore — it MUST
NOT be stored in plaintext, logged, or committed to source control. The client MUST
support a Base URL pointing at a local network IP; cleartext HTTP MUST be restricted to
private IP ranges via `networkSecurityConfig` and MUST NOT be globally enabled.
**Rationale**: BYOK is the app's core trust and cost model — no vendor lock-in, no data
passing through a third-party backend the developer controls, and the user retains
full control of which AI provider sees their screen content.

### X. Foreground Service & Result UX Contract
The screen-capture foreground service MUST declare `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`
and MUST only be started while an active `MediaProjection` grant exists (Android 14+
kills services that declare the type without an active projection). A
`MediaProjection.Callback` MUST be registered to detect revocation and stop the service
cleanly. The AI result MUST be presented in a Compose `ModalBottomSheet` with explicit,
mutually exclusive states: loading (skeleton), success (token-by-token SSE streaming
when the endpoint supports it), and error (visible message + retry). Dismissing the
sheet MUST cancel any in-flight request and release associated memory; the underlying
app the user was in MUST remain exactly as it was, with no navigation away from it.
**Rationale**: These two concerns are Android's hard platform requirements
(foreground service type correctness) and the app's core UX promise (never removing
the user from their context) — both are non-negotiable for the app to function at all.

## Technology Stack & Permissions

Concrete stack: Kotlin, Jetpack Compose, Coroutines/Flow, Hilt, Retrofit, OkHttp, Coil.
`minSdk 26`, `targetSdk 35`, Gradle Kotlin DSL. The `AndroidManifest.xml` MUST declare
exactly the permissions the app needs and no more: `SYSTEM_ALERT_WINDOW`,
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `INTERNET`, and the
`BIND_ACCESSIBILITY_SERVICE`-protected accessibility service declaration with its
`accessibility-service` metadata. Any additional permission requires a constitution
amendment recording why it is necessary.

## Development Workflow & Quality Gates

All user-provided and network-provided input MUST be validated at the boundary where
it enters the system (Settings form fields, AI HTTP responses) before being trusted
downstream. `OkHttp` clients MUST configure aggressive connect/read/write timeouts,
exponential-backoff retry for transient failures, and MUST support request
cancellation tied to the requesting coroutine/ViewModel scope. Every implementation
task MUST land as an atomic commit using Conventional Commits format
(`feat:`, `fix:`, `docs:`, `ci:`, `refactor:`, `test:`, `chore:`). No API key, secret,
or credential may ever be committed to source control — BYOK credentials exist only at
runtime on-device.

## Governance

This constitution supersedes any conflicting ad-hoc practice. Amendments require: (1) a
documented rationale for the change, (2) a version bump following semantic versioning
— MAJOR for backward-incompatible principle removal/redefinition, MINOR for a new
principle or materially expanded guidance, PATCH for clarification/wording — and (3)
propagation of any consequential changes to `.specify/templates/*` and this file's own
Sync Impact Report. All plans and task breakdowns MUST include an explicit Constitution
Check confirming compliance before implementation begins; unresolved violations MUST be
either fixed or justified in a documented "Complexity Tracking" exception before
proceeding. Reviews (self- or user-driven) MUST verify compliance with these principles
before a feature is considered done.

**Version**: 1.0.0 | **Ratified**: 2026-07-08 | **Last Amended**: 2026-07-08
