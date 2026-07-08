# Phase 1 Data Model: Visual Search Assistant

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

Entities derived from the spec's Key Entities section, refined with the multi-profile,
fallback, and ephemeral-chat decisions from research.md. This is a conceptual model
(fields + relationships + validation + lifecycle), not source code.

## AiEndpointProfile

A single named BYOK connection. The user's full set of profiles is the only piece of
this model that is persisted to disk (encrypted).

| Field | Type | Notes |
|---|---|---|
| `id` | opaque identifier | stable across edits |
| `name` | text | non-blank, unique among the user's profiles |
| `baseUrl` | URL | must parse as `http`/`https`; if `http`, must resolve to a private IP/localhost (enforced by network security config at the OS level; validated client-side as an early, friendlier error) |
| `apiKey` | secret text | stored encrypted at rest; never rendered back in plain text once saved (FR-012); may be blank for endpoints that don't require one (e.g. some local servers) |
| `modelName` | text | non-blank; sent verbatim as the request's `model` field |
| `isActive` | boolean | exactly one profile has `isActive = true` at any time (FR-009) |
| `fallbackOrder` | nullable integer | ascending sort key defining the fallback sequence among non-active profiles; `null` = excluded from fallback (FR-013) |
| `lastConnectionTest` | nullable record `{ timestamp, success, message }` | populated by the on-demand "test connection" action (FR-010); not re-validated automatically |

**Validation rules**: creating/editing a profile requires `name`, `baseUrl`, and
`modelName` to be non-blank before it can be saved (FR-008). Setting a profile active
clears `isActive` on the previously-active profile (invariant: exactly one active).

**Relationships**: a `VisualSearchRequest`'s eventual `SearchResult`(s) each record
which `AiEndpointProfile.id` produced them (FR-015).

## SearchPreferences

Singleton record of user-adjustable search behavior.

| Field | Type | Notes |
|---|---|---|
| `textFallbackEnabled` | boolean | default `true`; governs FR-019 vs FR-020 |
| `compressionQuality` | integer 0–100 | default `80`, per constitution principle VIII |

## VisualSearchRequest

One invocation of the core flow. Exists only in memory for the lifetime of its
`ConversationSession` (no history persistence, per spec Assumptions).

| Field | Type | Notes |
|---|---|---|
| `id` | opaque identifier | |
| `capturedAt` | timestamp | |
| `selectionRegion` | rect in captured-bitmap pixel space | already density/resolution-mapped per research.md's coordinate-mapping decision |
| `status` | `Pending \| Succeeded \| Failed` | drives the result panel's loading/success/error state (FR-006) |
| `captureSource` | `Image \| TextFallback` | which path produced the content sent to the AI (FR-019) |

## ConversationSession

The ephemeral, in-memory chat tied to exactly one `VisualSearchRequest`. Owned by the
result panel's `ViewModel`; never written to disk (FR-028).

| Field | Type | Notes |
|---|---|---|
| `id` | opaque identifier | |
| `visualSearchRequestId` | reference | 1:1 with the originating request |
| `messages` | ordered list of `ChatMessage` | the full, replayable conversation (R2) |
| `profilesImageSentTo` | set of `AiEndpointProfile.id` | tracks which profiles have already received the image this session (R3); empty at session start |
| `sessionPinnedProfileId` | nullable reference | set once a fallback profile first succeeds mid-session (R2); cleared on dismiss |
| `state` | `AwaitingFirstResult \| Active \| Discarded` | `Discarded` is terminal, set the instant the result panel is dismissed (FR-029) |

**Lifecycle**: `AwaitingFirstResult` (created the moment a selection is confirmed) →
`Active` (after the first `SearchResult` arrives; accepts follow-ups per FR-026) →
`Discarded` (panel dismissed; `messages`, `profilesImageSentTo`, and
`sessionPinnedProfileId` are all cleared/released together, atomically, per FR-029).
There is no path back from `Discarded`.

## ChatMessage

One turn within a `ConversationSession`.

| Field | Type | Notes |
|---|---|---|
| `id` | opaque identifier | |
| `role` | `User \| Assistant \| System` | matches the OpenAI-compatible wire format (R4) |
| `textContent` | text | |
| `includesImage` | boolean | true only for the turn(s) where the image part is attached (R3) |
| `producedByProfileId` | nullable reference | set on `Assistant` messages only; identifies which profile answered (FR-015) — this is the field that makes a `ChatMessage` double as the spec's **Search Result** concept: an assistant `ChatMessage` plus `usedTextFallback`/`producedByProfileId` metadata *is* a Search Result for that turn, rather than a separate persisted structure |
| `usedTextFallback` | boolean | set on `Assistant` messages only; true if this turn's content was derived from the AccessibilityService text fallback rather than image analysis (FR-019) |
| `createdAt` | timestamp | |

## PermissionStatus

Tracked per required system permission, surfaced during onboarding and re-checked at
time of use (FR-013/FR-018).

| Field | Type | Notes |
|---|---|---|
| `permissionType` | `Overlay \| Accessibility \| MediaProjectionCapability` | `MediaProjectionCapability` here means "the app is allowed to *prompt* for projection consent", not a granted projection itself — an actual `MediaProjection` grant is never a durable status per research.md R1, it is re-obtained every capture |
| `explainedToUser` | boolean | onboarding has shown the plain-language explanation for this permission |
| `granted` | boolean | current OS-reported status |
| `lastCheckedAt` | timestamp | |

## Entity relationship summary

```text
AiEndpointProfile (persisted, N per user, 1 active, ordered fallback)
        ▲ producedByProfileId
        │
VisualSearchRequest (in-memory, 1 per trigger)
        │ 1:1
        ▼
ConversationSession (in-memory, discarded on dismiss)
        │ 1:N ordered
        ▼
ChatMessage (= Search Result for Assistant-role turns)

SearchPreferences (persisted, singleton) — read by VisualSearchRequest capture/compression logic
PermissionStatus (derived from OS state + onboarding progress, N=3, not user-editable data)
```
