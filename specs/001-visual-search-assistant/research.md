# Phase 0 Research: Visual Search Assistant

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

This document resolves every open technical question raised by the spec and by the
constitution's non-negotiable architecture decisions, before Phase 1 design begins.
Each entry follows Decision / Rationale / Alternatives Considered.

---

## R1. MediaProjection + ForegroundService lifecycle on Android 14/15

**Decision**: Capture consent is requested **fresh, every single trigger**, and is
deferred until *after* the user has drawn and confirmed a selection — not at trigger
time. The selection overlay itself never depends on MediaProjection.

**Verified platform facts** (developer.android.com/media/grow/media-projection,
fetched 2026-07-08):

- "`createVirtualDisplay()` throws a `SecurityException` if your app... passes an
  `Intent` instance returned from `createScreenCaptureIntent()` to
  `getMediaProjection()` more than once, [or] calls `createVirtualDisplay()` more than
  once on the same `MediaProjection` instance." A session is defined as **a single
  call to `createVirtualDisplay()`; a `MediaProjection` token must be used only once.**
- "A stopped media projection session can no longer create a new virtual display, even
  if your app has not previously created a virtual display for that media projection."
  `onStop()` fires on user-initiated stop, screen lock, another projection session
  starting, or process death — and is terminal.
- If `MediaProjection.Callback` is not registered, `createVirtualDisplay()` throws
  `IllegalStateException`.
- The foreground service must call `startForeground(..., FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)`
  **before** `createVirtualDisplay()` is invoked, or the system throws
  `MissingForegroundServiceTypeException` / `SecurityException`.

**Consequence**: there is no way to "keep a capture session warm" across multiple
trigger invocations on Android 14+ — every trigger that needs a real frame requires a
brand-new system consent dialog. This is an intrinsic platform limitation for
non-privileged third-party apps (the real, first-party "Circle to Search" avoids this
because it runs with system-level screenshot privileges unavailable to us). This
constraint directly threatens SC-001 ("selection overlay appears in under 1 second")
if capture were the thing gating overlay display.

**Resolution — decouple overlay display from capture**: The overlay is a
`TYPE_APPLICATION_OVERLAY` window that is **transparent over the live screen**, not a
window that displays a pre-captured screenshot. `SYSTEM_ALERT_WINDOW` is granted once
during onboarding, so the overlay itself needs no per-use system prompt and can appear
immediately on trigger (satisfies SC-001). The user draws their lasso/rectangle
directly against what they can already see through the transparent overlay. Only once
the user **confirms** the selection does the app request MediaProjection consent,
capture the single frame, and crop it to the already-known selection bounds. This
matches the constitution's "on-demand, single frame, immediate teardown" principle even
more strictly than originally envisioned, and turns the unavoidable system dialog into
an expected part of "submitting" a search rather than a blocker on seeing the overlay.

**Concrete sequence** (also documented in plan.md Key Design Decisions):

1. Trigger fires → transparent selection overlay shown instantly (no MediaProjection
   involved yet).
2. User draws + confirms a selection.
3. A translucent, `exported=false` `MediaProjectionConsentActivity` is launched to host
   the Activity-Result consent flow (`createScreenCaptureIntent()` requires an Activity
   context); it finishes itself immediately after obtaining `RESULT_OK` + result data.
4. `ScreenCaptureForegroundService.onStartCommand()` calls
   `ServiceCompat.startForeground(id, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)`
   **first**.
5. Service calls `getMediaProjection(RESULT_OK, data)`, then immediately
   `registerCallback(callback, handler)` — both required before any virtual display.
6. Service creates exactly one `ImageReader` (`maxImages = 1`) and calls
   `createVirtualDisplay(...)` exactly once, sized to current display metrics.
7. On the single `onImageAvailable` (bounded by a 2s timeout to guard against a frame
   that never arrives), the service immediately: releases the `VirtualDisplay`, closes
   the `ImageReader`, calls `mediaProjection.stop()` explicitly, and stops itself.
8. `MediaProjection.Callback.onStop()` performs idempotent cleanup regardless of
   whether it fired from our own `.stop()` or an external revocation (status-bar chip,
   screen lock) — treats the projection as permanently dead either way, never attempts
   reuse.
9. If the user declines the consent dialog, this is treated like a capture failure:
   route to the text-extraction fallback if enabled (FR-019), otherwise show the "could
   not complete" message (FR-020) — never a silent no-op.

**Alternatives considered**:
- *Keep MediaProjection alive across triggers, tearing down only VirtualDisplay per
  capture* — rejected: directly contradicts the verified platform behavior
  (`createVirtualDisplay()` may be called only once per `MediaProjection` instance);
  would crash with `SecurityException` on the second trigger.
- *Capture the frame first, then show the overlay on top of a static bitmap* — rejected:
  would force the system consent dialog to gate overlay appearance on every single
  trigger, making SC-001 unachievable and adding a jarring interruption before the user
  has even started selecting.
- *Hold a single long-lived foreground "recording" indicator for the whole app
  session* — rejected: not possible per the "one createVirtualDisplay per token" rule,
  and would also conflict with SC-006 (negligible idle background activity) and user
  trust (a permanent screen-recording indicator is intrusive and misleading when the
  app is not actually capturing most of the time).

---

## R2. Chat context continuity when fallback switches profiles mid-conversation

**Decision**: The `ConversationSession` message history is a single, profile-agnostic
flat list living in the result `ViewModel` (in-memory, session-scoped). It is never
partitioned or duplicated per profile. When a mid-conversation turn triggers fallback,
the **entire existing message list is replayed verbatim** to the next candidate
profile, because the wire format (OpenAI-compatible `role`/`content` messages) is the
same regardless of provider (R4).

Two refinements:
- **Session pinning**: once a fallback profile succeeds mid-session, it becomes the
  "session-pinned" profile for the *remainder of that Conversation Session only* — the
  session does not retry the original (already-known-bad) active profile on every
  subsequent turn, which would add latency and repeated failures. The user's persisted
  Settings "active profile" is untouched; pinning is purely transient in-memory state
  discarded with the rest of the session on dismiss (FR-029). The next brand-new visual
  search always starts again from the real configured active profile.
- **Fallback continuation, not restart**: if the pinned fallback profile later fails too
  (e.g. mid-session), the search continues down the *same* fallback order from that
  point rather than restarting from the original active profile, until either a profile
  succeeds or the order is exhausted (FR-016).

**Rationale**: this directly satisfies the requirement that history "must follow" the
conversation, since history is owned by the app/session, not by any provider — no
provider has server-side memory of the conversation in the stateless model this
constitution mandates (IX). Session-pinning avoids UX-visible thrashing (repeatedly
re-trying a profile already known to be down within the same conversation).

**Alternatives considered**:
- *Reset the conversation when a fallback occurs* — rejected: directly violates the
  explicit requirement that history must be preserved across a fallback.
- *Permanently promote the fallback profile to "active" in Settings* — rejected: would
  silently change the user's configuration without an explicit action, contradicting
  "never fail/act silently" (constitution principle V) applied to configuration
  changes; the user owns their active-profile choice.

---

## R3. Image resend strategy across chat turns

**Decision**: The captured (already downscaled + WebP-compressed) selection image is
attached to a profile's request **only the first time that specific profile is used
within the Conversation Session** — the initial turn, and again exactly once if/when a
fallback switches to a profile that has not yet received the image in this session.
All other turns to a profile that has already seen the image send text-only messages
(the growing `role`/`content` history, including that profile's own prior text
answers, still gets resent every turn — only the image part is omitted after its first
use per profile).

Implementation shape: `ConversationSession` tracks a `Set<profileId>` of "has received
the image" per profile encountered in the session; the request builder checks
membership before deciding whether to include the image content part.

**Rationale (token economy, explicitly requested)**: because the constitution mandates
a stateless `/v1/chat/completions`-style client (IX), there is no server-side session —
every request resends the full message array from scratch. Vision-capable models
typically charge a fixed, often substantial, token cost per image (independent of how
short the accompanying text is), so resending the image on *every* turn multiplies that
fixed cost by the number of turns, dominating the cost of an otherwise cheap follow-up
question. Sending it once per profile keeps that fixed cost paid exactly as many times
as strictly necessary (once per distinct model actually used in the session), while a
typical follow-up ("what brand is that", "translate that") is answerable from the
already-generated first-turn description that is already present in the text history.

**Accepted tradeoff (documented, not hidden)**: because the endpoint is stateless, a
model literally cannot "see" the image again on turns where it isn't resent — it only
has the text of its own earlier answer. A follow-up that needs a *new* visual detail
not covered by the first answer (e.g. "what color is the small logo in the corner" when
the first answer never mentioned it) may get a lower-quality or hedged answer on a
text-only turn. This is judged an acceptable default given the explicit request to
optimize for token economy; the request-building policy is implemented as a single,
isolated, swappable strategy function (`ImageAttachmentPolicy`) specifically so it can
be changed to "always attach" later without touching the rest of the chat pipeline, if
real-world usage shows the tradeoff is wrong.

**Alternatives considered**:
- *Always resend the image every turn* — most correct/robust, rejected as the default
  because it was explicitly not what was asked for (token economy was the stated goal)
  and because it also increases per-turn latency on slower self-hosted/local endpoints.
- *Never resend after the very first request of the whole session (ignore profile
  changes)* — rejected: a fallback profile that never received the image would be
  reasoning over an image it never actually saw, producing potentially fabricated
  detail; the per-profile tracking closes this gap at a small, bounded extra cost (at
  most once per profile actually used).

---

## R4. Single OpenAI-compatible network path (no per-provider adapters)

**Decision**: One Retrofit service interface, one set of request/response DTOs, one
interceptor chain. Per-profile variation (Base URL, API key, model name) is pure data,
never a code branch or subclass.

- **Dynamic Base URL across profiles**: rather than building a new Retrofit instance
  per profile (wasteful, defeats connection pooling) or a global static base URL, the
  API method is declared with a full dynamic `@Url` parameter
  (`suspend fun chatCompletion(@Url url: HttpUrl, @Body request: ChatCompletionRequest, ...)`),
  and the single `Retrofit` instance is built with any placeholder base URL (Retrofit
  requires one at build time even when every call overrides it via `@Url`). The caller
  (repository layer) resolves the concrete `{profile.baseUrl}/chat/completions` URL
  from the currently-attempted `AiEndpointProfile` before each call.
- **Per-request authorization**: a single `Interceptor` reads the target profile's API
  key from a request `Tag` attached by the caller (`Request.Builder().tag(ProfileTag::class.java, tag)`)
  and sets the `Authorization: Bearer <key>` header — never a globally-configured
  static header — because a single conversation can legitimately target different
  profiles across its turns (R2/fallback).
- **Request/response schema**: standard OpenAI chat-completions shape —
  `{ model, messages: [{ role, content }], stream }`, where `content` is either a plain
  string or a multi-part array (`[{type:"text",...}, {type:"image_url", image_url:{url:"data:image/webp;base64,..."}}]`)
  for the turns that include the image, per R3. This exact shape is what OpenAI, the
  OpenAI-compatibility layer of Gemini, OpenRouter, and Ollama's OpenAI-compatible
  endpoint all accept, so no per-provider request transformation is needed.
- **Streaming**: a single response-handling layer inspects the response
  `Content-Type`. `text/event-stream` is parsed as `data: {...}` SSE chunks
  incrementally (token-by-token UI updates); any other content type (a provider/local
  server that ignored `stream: true`) is parsed as one plain JSON object. This branch
  happens once, at the transport layer, not per provider.
- **Cleartext for local/private endpoints**: `networkSecurityConfig` allows
  `cleartextTrafficPermitted` only for private IP ranges (RFC1918 `10.0.0.0/8`,
  `172.16.0.0/12`, `192.168.0.0/16`) and `localhost`, so a profile pointed at a home
  Ollama instance works over plain HTTP without a blanket cleartext allowance.

**Rationale**: this is a direct, literal implementation of constitution principle IX
("cliente de rede ÚNICO no formato OpenAI-compatible... sem adaptadores separados por
provedor"). Keeping all per-profile variation as data (never a `when(provider)`
branch or a provider-specific subclass) is what makes new providers/local servers work
automatically as long as they speak the same wire format, with zero code changes.

**Alternatives considered**:
- *One Retrofit client instance per configured profile* — rejected: wasteful (own
  `OkHttpClient`/connection pool per profile), and awkward for fallback, which needs to
  try several profiles in sequence within one logical request.
- *A `sealed class Provider` with per-provider request-mapping implementations* —
  rejected outright: this is precisely the "adaptador separado por provedor" the
  constitution prohibits; every provider in scope already speaks the same
  `/v1/chat/completions` dialect, so no mapping layer is needed.

---

## Supporting decisions

- **Credential storage**: Jetpack DataStore (Preferences) wrapping values encrypted via
  `androidx.security.crypto` (`Tink`-backed, Android Keystore master key), rather than
  raw `EncryptedSharedPreferences` — DataStore is the currently-recommended persistence
  API (async, `Flow`-based, no `apply()`/main-thread disk I/O risk) while still keeping
  every stored value encrypted at rest via Keystore. Multiple `AiEndpointProfile`
  records are stored as a serialized (kotlinx.serialization) list inside one encrypted
  DataStore entry.
- **Black/blank frame detection (FLAG_SECURE)**: sample a small grid of pixels (e.g. a
  9x9 sparse grid, not the full bitmap, to keep this cheap) from the captured `Bitmap`
  immediately after capture; if effectively 100% of sampled pixels are a single flat
  color (typically black), treat the frame as blocked and route to the
  AccessibilityService text-extraction fallback (constitution III/V, FR-019/FR-020).
  This runs on `Dispatchers.Default`, before any downscale/compression work.
- **Minimum selection size**: a selection is rejected (FR-021) if its bounding box is
  smaller than 24dp × 24dp in screen-independent units (matches the standard Android
  minimum touch-target size, a well-established platform convention, so it doubles as
  an "obviously not a deliberate drag" signal).
- **Coordinate mapping**: the overlay records the selection path in the overlay
  window's own coordinate space (device pixels at the overlay's density); the capture
  service records the `VirtualDisplay`'s actual pixel dimensions and density at capture
  time. The crop rectangle is derived by scaling the overlay-space bounding box by the
  ratio `(virtualDisplayPx / overlayWindowPx)` independently on each axis, which is
  correct across multi-window/split-screen and foldable posture changes because both
  measurements are read fresh at the moment of that specific capture, never cached
  from a previous session.
- **Exact dependency versions**: not pinned in this plan. Kotlin, AGP, the Compose BOM,
  Hilt, Retrofit, and OkHttp versions must be resolved to their current stable releases
  compatible with `targetSdk 35` at the start of the implementation phase (task-level
  concern), rather than asserted here, to avoid citing versions that may already be
  stale by the time implementation begins.
