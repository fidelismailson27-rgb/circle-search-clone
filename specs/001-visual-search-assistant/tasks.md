# Tasks: Visual Search Assistant

**Input**: Design documents from `specs/001-visual-search-assistant/`

**Prerequisites**: [plan.md](./plan.md) (required), [spec.md](./spec.md) (required for user stories), [research.md](./research.md), [data-model.md](./data-model.md), [quickstart.md](./quickstart.md)

**Tests**: Full TDD is not mandated by the spec, so most tasks below are
implementation-only. A small number of test tasks are included where explicitly
requested (the `BlackFrameDetector` ambiguous-case unit test) or where a component has
no other practical way to validate correctness without a device (network client
fallback/streaming logic, image-attachment policy) — these are marked accordingly.

**Organization**: Tasks are grouped by user story (from spec.md, in priority order) to
enable independent implementation and testing of each story, per plan.md's single
Android app module with `data`/`domain`/`ui` layering.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: Maps the task to a spec.md user story (US1–US5)
- Every task includes an exact file path

## Path Conventions

Single Android app module, per plan.md Project Structure:
`app/src/main/kotlin/com/circulesearch/app/{data,domain,ui,di}/...`,
`app/src/test/kotlin/...` (unit tests), `app/src/androidTest/kotlin/...` (Compose UI
tests), `app/src/main/res/...`, `app/src/main/AndroidManifest.xml`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic Gradle/Android scaffolding.

- [ ] T001 Resolve and pin the current stable versions of Kotlin, Android Gradle
  Plugin (AGP), the Jetpack Compose BOM, and Hilt — verified compatible with
  `compileSdk`/`targetSdk 35` — plus Retrofit, OkHttp, kotlinx.serialization,
  Jetpack DataStore, and `androidx.security.crypto`, and record them all in
  `gradle/libs.versions.toml`. **This is the first implementation task**: no other
  task may hardcode a dependency version outside this catalog.
- [ ] T002 Create root Gradle Kotlin DSL project skeleton: `settings.gradle.kts`,
  root `build.gradle.kts` wired to `gradle/libs.versions.toml` from T001 (depends on
  T001)
- [ ] T003 [P] Create `app/build.gradle.kts` applying the Android application, Kotlin,
  Compose compiler, Hilt, and kotlinx-serialization plugins, with `minSdk 26` /
  `targetSdk 35`, referencing the version catalog from T001 (depends on T002)
- [ ] T004 [P] Configure ktlint/detekt static analysis in root `build.gradle.kts` and
  `.editorconfig`
- [ ] T005 Create `app/src/main/AndroidManifest.xml` skeleton: package id, application
  block (label, icon placeholder, theme), and the constitution's required permissions
  (`SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`,
  `INTERNET`) — no components declared yet, those are added by the tasks that
  implement them
- [ ] T006 [P] Create `app/src/main/res/xml/network_security_config.xml` restricting
  cleartext traffic to private IP ranges (`10.0.0.0/8`, `172.16.0.0/12`,
  `192.168.0.0/16`) and `localhost`, and reference it via
  `android:networkSecurityConfig` in `app/src/main/AndroidManifest.xml`
- [ ] T007 [P] Create the `app/src/main/kotlin/com/circulesearch/app/{data,domain,ui,di}`
  package skeleton per plan.md's Project Structure (empty sub-packages:
  `data/network`, `data/capture`, `data/accessibility`, `data/settings`,
  `data/repository`, `domain/model`, `domain/repository`, `domain/usecase`,
  `ui/trigger`, `ui/overlay`, `ui/result`, `ui/settings`, `ui/onboarding`, `ui/theme`)
- [ ] T008 Verify the Gradle wrapper (`gradlew`, `gradle/wrapper/gradle-wrapper.properties`)
  is pinned to a Gradle version compatible with the AGP chosen in T001, suitable for a
  clean `ubuntu-latest` GitHub Actions run

**Checkpoint**: project compiles as an empty Android app shell.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure and the app's one public interface contract, which
every user story either directly needs or builds on top of.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T009 Define and document the external trigger Intent/Activity contract — the
  app's **only public interface** — in
  `specs/001-visual-search-assistant/contracts/trigger-intent-contract.md`: the exact
  exported component name, intent action string, required/optional extras, expected
  caller behavior (what a Square Home shortcut or an Ubikitouch gesture binding must
  invoke), and what the caller can assume about the response (fire-and-forget, no
  result expected back). This is a prerequisite for T022.
- [ ] T010 [P] Create domain model classes per data-model.md — `AiEndpointProfile`,
  `SearchPreferences`, `VisualSearchRequest`, `ConversationSession`, `ChatMessage`,
  `PermissionStatus` — in
  `app/src/main/kotlin/com/circulesearch/app/domain/model/`
- [ ] T011 [P] Define repository interfaces `EndpointProfileRepository`,
  `VisualSearchRepository`, `PermissionStatusRepository` in
  `app/src/main/kotlin/com/circulesearch/app/domain/repository/` (depends on T010)
- [ ] T012 [P] Implement `CircleSearchApplication` (`@HiltAndroidApp`) in
  `app/src/main/kotlin/com/circulesearch/app/CircleSearchApplication.kt` and register
  it as `android:name` in `app/src/main/AndroidManifest.xml`
- [ ] T013 [P] Implement `DispatchersModule` (Hilt-qualified IO/Default/Main
  coroutine dispatchers) in
  `app/src/main/kotlin/com/circulesearch/app/di/DispatchersModule.kt`
- [ ] T014 Implement encrypted DataStore infrastructure —
  `EndpointProfileLocalDataSource.kt` and `SearchPreferencesLocalDataSource.kt`,
  Keystore-backed via `androidx.security.crypto`, serialized with
  kotlinx.serialization — in
  `app/src/main/kotlin/com/circulesearch/app/data/settings/` (depends on T010)
- [ ] T015 Implement `EndpointProfileRepositoryImpl` and a minimal
  `PermissionStatusRepositoryImpl` in
  `app/src/main/kotlin/com/circulesearch/app/data/repository/` (depends on T011, T014)
- [ ] T016 [P] Define the `OpenAiCompatibleApi` Retrofit interface and request/response
  DTOs (`ChatCompletionRequest`, `ChatMessageDto`, `ChatCompletionResponse`,
  `ChatCompletionChunk`) per research.md R4, in
  `app/src/main/kotlin/com/circulesearch/app/data/network/`
- [ ] T017 [P] Implement `ProfileAuthInterceptor` (reads the target profile from a
  per-request `Tag`, sets `Authorization: Bearer <key>`) in
  `app/src/main/kotlin/com/circulesearch/app/data/network/ProfileAuthInterceptor.kt`
- [ ] T018 Implement `NetworkModule` — single `OkHttpClient` (aggressive
  connect/read/write timeouts, exponential-backoff retry interceptor) and single
  `Retrofit` instance with a placeholder base URL, dynamic `@Url`-per-call — in
  `app/src/main/kotlin/com/circulesearch/app/data/network/NetworkModule.kt` (depends
  on T016, T017)
- [ ] T019 [P] Implement `SseChatStreamParser` (branches once on response
  `Content-Type`: `text/event-stream` incremental parsing vs. plain JSON) in
  `app/src/main/kotlin/com/circulesearch/app/data/network/SseChatStreamParser.kt`
- [ ] T020 [P] Set up the Compose theme (`Color.kt`, `Type.kt`, `Theme.kt`) and a root
  `NavHost` composable (routes not yet wired to an Activity) in
  `app/src/main/kotlin/com/circulesearch/app/ui/theme/` and
  `app/src/main/kotlin/com/circulesearch/app/ui/AppNavHost.kt`
- [ ] T021 Implement `CheckRequiredPermissionsUseCase` in
  `app/src/main/kotlin/com/circulesearch/app/domain/usecase/CheckRequiredPermissionsUseCase.kt`
  (depends on T011, T015)

**Checkpoint**: Foundation ready — user story implementation can now begin.

---

## Phase 3: User Story 1 - Circle Anything, Anywhere (Priority: P1) 🎯 MVP

**Goal**: External trigger → instant transparent selection overlay → user-drawn
selection → on-demand single-frame capture → crop/compress → send to the active AI
endpoint → result panel → dismiss returns the user to exactly where they were.

**Independent Test**: Configure a valid AI endpoint, invoke the trigger from any
foreground app, draw a selection, confirm an answer is displayed without the
originating app losing state.

### Implementation for User Story 1

- [ ] T022 [US1] Implement `TriggerEntryActivity` (exported, no visible UI, translucent
  theme) satisfying the contract from T009, in
  `app/src/main/kotlin/com/circulesearch/app/ui/trigger/TriggerEntryActivity.kt`;
  declare it in `app/src/main/AndroidManifest.xml` with `android:exported="true"` and
  the intent-filter/action from T009
- [ ] T023 [P] [US1] Implement `SelectionOverlayWindow` (`WindowManager`
  `TYPE_APPLICATION_OVERLAY` host, transparent over the live screen per research.md
  R1) in
  `app/src/main/kotlin/com/circulesearch/app/ui/overlay/SelectionOverlayWindow.kt`
- [ ] T024 [P] [US1] Implement `SelectionCanvas` Compose UI (`Path`-based freeform/
  rectangular lasso drawing) in
  `app/src/main/kotlin/com/circulesearch/app/ui/overlay/SelectionCanvas.kt`
- [ ] T025 [US1] Implement `CoordinateMapper` (overlay-space → `VirtualDisplay`
  pixel-space, density-aware, re-read fresh per capture for multi-window/foldable
  correctness) in
  `app/src/main/kotlin/com/circulesearch/app/ui/overlay/CoordinateMapper.kt` (depends
  on T023, T024)
- [ ] T026 [US1] Implement minimum-selection-size validation (24dp × 24dp, FR-021) in
  `SelectionCanvas.kt` / `CoordinateMapper.kt`, prompting the user to redraw when too
  small
- [ ] T027 [US1] Implement `MediaProjectionConsentActivity` (translucent, hosts the
  `createScreenCaptureIntent()` Activity-Result flow, launched only after selection
  confirm per research.md R1) in
  `app/src/main/kotlin/com/circulesearch/app/data/capture/MediaProjectionConsentActivity.kt`;
  declare in `AndroidManifest.xml` with `exported="false"`
- [ ] T028 [US1] Implement `ScreenCaptureForegroundService` — calls
  `startForeground(..., FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)` before any
  `MediaProjection` API, registers `MediaProjection.Callback` before
  `createVirtualDisplay()` — in
  `app/src/main/kotlin/com/circulesearch/app/data/capture/ScreenCaptureForegroundService.kt`;
  declare with `android:foregroundServiceType="mediaProjection"` in
  `AndroidManifest.xml` (depends on T027)
- [ ] T029 [US1] Implement `SingleFrameCapturer` — one `ImageReader` (`maxImages = 1`),
  exactly one `createVirtualDisplay()` call, 2s timeout guard, immediate
  `VirtualDisplay`/`ImageReader`/`MediaProjection` teardown after the single frame —
  in `app/src/main/kotlin/com/circulesearch/app/data/capture/SingleFrameCapturer.kt`
  (depends on T028)
- [ ] T030 [US1] Implement `BlackFrameDetector` v1 (sparse pixel-grid sampling
  heuristic) in
  `app/src/main/kotlin/com/circulesearch/app/data/capture/BlackFrameDetector.kt`,
  wired to a clear "couldn't capture this screen" error on detection — baseline
  never-fail-silently behavior for the MVP; hardened and routed to the text fallback
  in US5 (T066–T068)
- [ ] T031 [US1] Implement the crop-to-selection → downscale → WebP ~q80 compression
  pipeline on `Dispatchers.Default`, with explicit `recycle()` of the full-resolution
  source `Bitmap` immediately after crop (constitution VI/VII/VIII), in
  `app/src/main/kotlin/com/circulesearch/app/data/capture/SelectionImageProcessor.kt`
  (depends on T025, T029)
- [ ] T032 [US1] Implement `ImageAttachmentPolicy` (attach the image only on a given
  profile's first use within a session, per research.md R3) in
  `app/src/main/kotlin/com/circulesearch/app/data/network/ImageAttachmentPolicy.kt`
- [ ] T033 [US1] Implement `VisualSearchRepositoryImpl`'s initial-search path (build
  the first `ChatCompletionRequest` via `ImageAttachmentPolicy`, call
  `OpenAiCompatibleApi` against the active profile, handle both streaming and
  non-streaming responses via `SseChatStreamParser`) in
  `app/src/main/kotlin/com/circulesearch/app/data/repository/VisualSearchRepositoryImpl.kt`
  (depends on T015, T018, T019, T032)
- [ ] T034 [US1] Implement `StartVisualSearchUseCase` (check an active profile exists
  → capture → black-frame check → compress → send → emit
  `Pending`/`Succeeded`/`Failed` states) in
  `app/src/main/kotlin/com/circulesearch/app/domain/usecase/StartVisualSearchUseCase.kt`
  (depends on T030, T031, T033)
- [ ] T035 [US1] Implement superseding behavior for a new trigger arriving while a
  prior search is still in progress (FR-018/FR-023) in
  `StartVisualSearchUseCase.kt`
- [ ] T036 [P] [US1] Implement `ResultBottomSheet` Compose UI with explicit
  loading (skeleton) / success / error states in
  `app/src/main/kotlin/com/circulesearch/app/ui/result/ResultBottomSheet.kt`
- [ ] T037 [US1] Implement `ResultViewModel` (creates the `ConversationSession` for the
  first turn, cancels any in-flight request and releases memory on dismiss per
  FR-007/FR-028) in
  `app/src/main/kotlin/com/circulesearch/app/ui/result/ResultViewModel.kt` (depends on
  T034)
- [ ] T038 [US1] Wire the dismiss-without-searching cancel path (overlay dismissed
  without a confirmed selection returns cleanly, no capture/network triggered, FR-004)
  across `SelectionOverlayWindow.kt` / `ResultViewModel.kt`
- [ ] T039 [US1] Implement the "no active profile configured yet" redirect-to-Settings
  notice (FR-017/FR-022) in `StartVisualSearchUseCase.kt` plus a minimal notice
  composable in `ResultBottomSheet.kt`
- [ ] T040 [US1] Validate app-state preservation on dismiss (scroll/input/navigation of
  the underlying app untouched) by running quickstart.md section 4 manually on a
  device; adjust overlay/service window flags as needed to eliminate any focus-stealing
  regressions found

**Checkpoint**: User Story 1 is fully functional and independently testable (MVP).

---

## Phase 4: User Story 2 - Bring Your Own AI Endpoint, With Automatic Fallback (Priority: P2)

**Goal**: Named, multi-profile BYOK configuration with connection testing and
automatic fallback when the active profile's request fails.

**Independent Test**: Create multiple named profiles in Settings, mark one active and
define a fallback order, test each individually, and confirm real searches fall back
automatically and transparently.

### Implementation for User Story 2

- [ ] T041 [P] [US2] Implement `SettingsScreen` (profile list, add/edit/delete entry
  points) in
  `app/src/main/kotlin/com/circulesearch/app/ui/settings/SettingsScreen.kt`
- [ ] T042 [P] [US2] Implement `ProfileEditorScreen` (name, base URL, API key, model
  fields, inline validation per FR-008) in
  `app/src/main/kotlin/com/circulesearch/app/ui/settings/ProfileEditorScreen.kt`
- [ ] T043 [P] [US2] Implement `FallbackOrderScreen` (reorder non-active profiles into
  a fallback sequence, FR-013) in
  `app/src/main/kotlin/com/circulesearch/app/ui/settings/FallbackOrderScreen.kt`
- [ ] T044 [US2] Implement `SettingsViewModel` (profile CRUD, mark-active, fallback
  reordering, preference editing) in
  `app/src/main/kotlin/com/circulesearch/app/ui/settings/SettingsViewModel.kt`
  (depends on T015, T041, T042, T043)
- [ ] T045 [US2] Implement `TestEndpointConnectionUseCase` (per-profile on-demand
  connection test with an explicit success/failure outcome, FR-010) in
  `app/src/main/kotlin/com/circulesearch/app/domain/usecase/TestEndpointConnectionUseCase.kt`
  (depends on T018)
- [ ] T046 [US2] Implement `SaveEndpointProfileUseCase` and `ReorderFallbackUseCase`
  (field validation, single-active-profile invariant, FR-008/FR-009/FR-011) in
  `app/src/main/kotlin/com/circulesearch/app/domain/usecase/`
- [ ] T047 [US2] Implement automatic fallback retry in `VisualSearchRepositoryImpl`:
  on network error, timeout, HTTP error response, or malformed/unparseable response
  from the active profile, retry the same search against the next profile in the
  user-defined fallback order (FR-014) (depends on T033)
- [ ] T048 [US2] Implement the discreet "answered by {profile name}" indicator (FR-015)
  in `ResultViewModel.kt` / `ResultBottomSheet.kt` (depends on T047)
- [ ] T049 [US2] Implement the aggregated-error path when the active profile and every
  fallback profile fail (FR-016) in `VisualSearchRepositoryImpl.kt` /
  `StartVisualSearchUseCase.kt` (depends on T047)
- [ ] T050 [US2] Implement never-display-credential-in-plaintext behavior (masked
  field, no re-hydration of the raw key on reopen, FR-012) in
  `ProfileEditorScreen.kt` / `SettingsViewModel.kt`
- [ ] T051 [US2] Wire compression-quality and text-fallback-toggle preference editing
  to `SearchPreferencesLocalDataSource` (FR-011) in `SettingsScreen.kt` /
  `SettingsViewModel.kt`

**Checkpoint**: User Stories 1 and 2 both work independently; US1's search path now
benefits from automatic fallback.

---

## Phase 5: User Story 3 - Continue the Conversation on a Search (Priority: P2)

**Goal**: In-memory, ephemeral follow-up chat within the result panel, discarded in
full when the panel closes.

**Independent Test**: Complete one visual search, send one or more follow-ups in the
same panel, confirm context-aware answers, then close and reopen to confirm no memory
persists.

### Implementation for User Story 3

- [ ] T052 [US3] Extend `ResultViewModel` to accept follow-up turns (append a `User`
  `ChatMessage`, keep `ConversationSession.state = Active`, FR-026) in
  `app/src/main/kotlin/com/circulesearch/app/ui/result/ResultViewModel.kt`
- [ ] T053 [US3] Implement `SendFollowUpMessageUseCase` (replays the full message
  history, applies `ImageAttachmentPolicy`'s per-profile tracking, FR-027) in
  `app/src/main/kotlin/com/circulesearch/app/domain/usecase/SendFollowUpMessageUseCase.kt`
  (depends on T032, T033)
- [ ] T054 [US3] Implement session-pinned-profile behavior for mid-conversation
  fallback (research.md R2: pin the succeeding profile for the rest of the session
  only, without altering the persisted active profile) in
  `VisualSearchRepositoryImpl.kt` (depends on T047, T053)
- [ ] T055 [US3] Audit `ResultViewModel.kt` / `ConversationSession` handling to confirm
  the conversation context is never written to any persistent store at any point
  (FR-028) — fix any accidental persistence found
- [ ] T056 [US3] Implement full session discard on `ResultBottomSheet` dismiss,
  including cancellation of any in-flight follow-up request (FR-029; edge case:
  dismiss while a follow-up is mid-flight) in `ResultViewModel.kt`
- [ ] T057 [P] [US3] Implement `ChatMessageList` and `ChatInputBar` Compose UI (running
  message list + input affordance, FR-030) in
  `app/src/main/kotlin/com/circulesearch/app/ui/result/ChatMessageList.kt` and
  `app/src/main/kotlin/com/circulesearch/app/ui/result/ChatInputBar.kt`
- [ ] T058 [US3] Wire `ChatMessageList`/`ChatInputBar` into `ResultBottomSheet`,
  replacing the single-answer view with the running conversation view (depends on
  T036, T057)

**Checkpoint**: User Stories 1, 2, and 3 all work independently.

---

## Phase 6: User Story 4 - Understand and Grant the Right Permissions (Priority: P3)

**Goal**: First-run onboarding that explains and requests overlay, accessibility, and
capture permissions before the core flow is used.

**Independent Test**: Fresh install → walk through onboarding → confirm each
permission is explained before its system prompt, and that grant/deny is detected
correctly.

### Implementation for User Story 4

- [ ] T059 [P] [US4] Implement `OnboardingScreen` (step sequence: overlay,
  accessibility, capture capability) in
  `app/src/main/kotlin/com/circulesearch/app/ui/onboarding/OnboardingScreen.kt`
- [ ] T060 [P] [US4] Implement `PermissionExplainerCard` (plain-language explanation
  shown before each system prompt, FR-017) in
  `app/src/main/kotlin/com/circulesearch/app/ui/onboarding/PermissionExplainerCard.kt`
- [ ] T061 [US4] Implement `OnboardingViewModel` (tracks `PermissionStatus` per
  permission, advances steps, skips already-granted permissions) in
  `app/src/main/kotlin/com/circulesearch/app/ui/onboarding/OnboardingViewModel.kt`
  (depends on T021, T059, T060)
- [ ] T062 [US4] Implement `TriggerAccessibilityService` skeleton (minimal event/flag
  subscription scoped to trigger reception only, constitution IV) in
  `app/src/main/kotlin/com/circulesearch/app/data/accessibility/TriggerAccessibilityService.kt`;
  declare the service and
  `app/src/main/res/xml/accessibility_service_config.xml` in `AndroidManifest.xml`
- [ ] T063 [US4] Implement at-time-of-use permission re-check and redirect (FR-018;
  edge case: a previously granted permission gets revoked) in
  `StartVisualSearchUseCase.kt` using `CheckRequiredPermissionsUseCase` (depends on
  T034, T061)
- [ ] T064 [US4] Implement `MainActivity` (`LAUNCHER` intent-filter) hosting the
  `AppNavHost` from T020, routing to `OnboardingScreen` when any required permission
  is missing and to a neutral Settings/home surface otherwise, in
  `app/src/main/kotlin/com/circulesearch/app/ui/MainActivity.kt`; declare in
  `AndroidManifest.xml` (depends on T020, T061)

**Checkpoint**: User Stories 1–4 all work independently; onboarding now gates first
use of the core flow.

---

## Phase 7: User Story 5 - Reliable Results Despite Protected Content (Priority: P4)

**Goal**: Graceful, clearly-communicated fallback to on-screen text extraction when a
screen blocks image capture (`FLAG_SECURE`), instead of a blank or broken result.

**Independent Test**: Trigger the assistant over a screen known to block capture;
confirm a text-derived answer with a visible notice, or (with the fallback preference
off) a clear "could not complete" message — never a blank panel.

### Tests for User Story 5 ⚠️

> Write this test first; confirm it fails against the v1 detector from T030 before
> hardening it in T067.

- [ ] T066 [P] [US5] Unit test `BlackFrameDetector` covering ambiguous cases —
  legitimately dark/black on-screen content (e.g. a near-black photo, a full-bleed
  dark-mode UI) that must **not** trigger the fallback, versus an actual
  `FLAG_SECURE`-blocked frame that **must** trigger it — in
  `app/src/test/kotlin/com/circulesearch/app/data/capture/BlackFrameDetectorTest.kt`

### Implementation for User Story 5

- [ ] T065 [US5] Implement `TextExtractionFallback` (on-demand
  `AccessibilityNodeInfo` text extraction, invoked only on black-frame detection, never
  continuous) in
  `app/src/main/kotlin/com/circulesearch/app/data/accessibility/TextExtractionFallback.kt`
  (depends on T062)
- [ ] T067 [US5] Harden `BlackFrameDetector`'s pixel-sampling heuristic until the
  ambiguous-case unit test from T066 passes, in
  `app/src/main/kotlin/com/circulesearch/app/data/capture/BlackFrameDetector.kt`
  (depends on T066, T030)
- [ ] T068 [US5] Wire a positive `BlackFrameDetector` result to
  `TextExtractionFallback` when the text-fallback preference is enabled, informing the
  user the fallback was used (FR-019) in `StartVisualSearchUseCase.kt` (depends on
  T065, T067)
- [ ] T069 [US5] Implement the disabled/unavailable-fallback clear-failure path
  (FR-020) in `StartVisualSearchUseCase.kt`
- [ ] T070 [P] [US5] Compose UI test asserting the result panel visibly labels a
  text-fallback-derived answer as such (FR-019 visibility requirement) in
  `app/src/androidTest/kotlin/com/circulesearch/app/ui/result/ResultBottomSheetTest.kt`

**Checkpoint**: All five user stories independently functional and integrated.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Test coverage, hardening, and delivery infrastructure that spans more
than one story.

- [ ] T071 [P] Unit tests for `EndpointProfileRepositoryImpl` (CRUD, single-active
  invariant, fallback ordering) in
  `app/src/test/kotlin/com/circulesearch/app/data/repository/EndpointProfileRepositoryImplTest.kt`
- [ ] T072 [P] Unit tests for `VisualSearchRepositoryImpl`'s fallback and
  aggregated-error logic using OkHttp `MockWebServer` (FR-014/FR-016) in
  `app/src/test/kotlin/com/circulesearch/app/data/repository/VisualSearchRepositoryImplTest.kt`
- [ ] T073 [P] Unit tests for `ImageAttachmentPolicy` (per-profile first-use tracking,
  FR-027/research.md R3) in
  `app/src/test/kotlin/com/circulesearch/app/data/network/ImageAttachmentPolicyTest.kt`
- [ ] T074 [P] Unit tests for `SseChatStreamParser` (SSE vs. plain-JSON branch) in
  `app/src/test/kotlin/com/circulesearch/app/data/network/SseChatStreamParserTest.kt`
- [ ] T075 [P] Compose UI tests for `SelectionCanvas` gesture-to-bounding-box behavior
  and minimum-selection-size rejection in
  `app/src/androidTest/kotlin/com/circulesearch/app/ui/overlay/SelectionCanvasTest.kt`
- [ ] T076 Add ProGuard/R8 rules for Retrofit/OkHttp/kotlinx.serialization
  reflection-sensitive classes in `app/proguard-rules.pro`
- [ ] T077 [P] Audit pass across `data/`/`domain/` for any silent `catch` blocks or
  unjustified `!!` usage, per constitution II/V, fixing any found
- [ ] T078 Run the full `quickstart.md` manual validation guide end-to-end on an API
  34+ device/emulator and on the API 26 minimum, logging and fixing any deviations
- [ ] T079 Create `.github/workflows/build.yml`: checkout, JDK 17 (temurin), Android
  SDK setup, `./gradlew assembleDebug`, and publish the resulting debug APK via
  `actions/upload-artifact`, triggered on push to `main` — this is the project's CI
  build workflow (FASE 4 of the overall project plan)
- [ ] T080 [P] Update `README.md` with project overview, BYOK setup instructions, and
  a plain-language explanation of why each permission is required

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately. T001 (version pinning)
  blocks every other Setup task.
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories.
  T009 (trigger contract) specifically blocks T022 (US1).
- **User Stories (Phase 3–7)**: All depend on Foundational completion.
  - US1 (P1) has no dependency on other stories — true MVP.
  - US2 (P2) extends US1's `VisualSearchRepositoryImpl` (T047 depends on T033) but its
    Settings UI/profile CRUD is independently testable per its own Independent Test.
  - US3 (P2) extends US1's `ResultViewModel`/`VisualSearchRepositoryImpl` and benefits
    from US2's fallback (T054 depends on T047), but is independently testable against
    a single profile with no fallback configured.
  - US4 (P3) is independent of US1–US3's search logic; it only depends on
    Foundational's `CheckRequiredPermissionsUseCase` (T021).
  - US5 (P4) extends US1's `BlackFrameDetector` (T030) and US4's
    `TriggerAccessibilityService` (T062).
- **Polish (Phase 8)**: Depends on all desired user stories being complete. T079 (CI)
  has no code dependency and can run as soon as Phase 1 produces a buildable project,
  but is sequenced last here since a CI workflow is only useful once there is
  meaningful code for it to build and gate.

### Within Each User Story

- Overlay/UI-facing pieces before the `ViewModel` that orchestrates them
- Domain use cases after the repository interfaces/implementations they depend on
- Story complete and checkpointed before moving to the next priority (if working
  sequentially)

### Parallel Opportunities

- All Setup tasks marked `[P]` (T003, T004, T006, T007) can run in parallel once their
  own listed dependency is satisfied
- Foundational tasks marked `[P]` (T010, T011, T012, T013, T016, T017, T019, T020) can
  run in parallel within Phase 2
- Once Foundational completes, US4 (permissions/onboarding) can be built fully in
  parallel with US1 (core flow), since they don't share files
- Within US1: T023/T024 (overlay UI) in parallel with T027/T028 (capture service
  scaffolding), before both feed into T025/T029
- Within Polish: T071–T075, T077, T080 are all independent files and can run in
  parallel

---

## Parallel Example: User Story 1

```bash
# Launch the overlay UI and the capture-service scaffolding in parallel:
Task: "Implement SelectionOverlayWindow in app/src/main/kotlin/com/circulesearch/app/ui/overlay/SelectionOverlayWindow.kt"
Task: "Implement SelectionCanvas in app/src/main/kotlin/com/circulesearch/app/ui/overlay/SelectionCanvas.kt"

# Once T009's contract is available, the trigger activity implementation is independent of the above:
Task: "Implement TriggerEntryActivity per the T009 contract in app/src/main/kotlin/com/circulesearch/app/ui/trigger/TriggerEntryActivity.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001 version pinning first)
2. Complete Phase 2: Foundational (T009 trigger contract is a hard blocker for US1)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: run quickstart.md sections 4, 6, 7, 10 against the MVP
5. This is a demoable, independently valuable product slice — a working
   trigger-to-answer loop against one hardcoded/manually-entered endpoint is not
   possible without at least a minimal profile, so US1's `StartVisualSearchUseCase`
   (T034) depends on Foundational's `EndpointProfileRepositoryImpl` (T015) existing,
   even though the *Settings UI* to manage profiles comes later in US2 — for MVP
   validation, a profile can be seeded directly via the repository in a test/debug
   path rather than through UI

### Incremental Delivery

1. Setup + Foundational → foundation ready, trigger contract fixed
2. US1 → validate independently → MVP demoable (with a seeded profile)
3. US2 → validate independently → real Settings UI + fallback resilience
4. US3 → validate independently → follow-up chat
5. US4 → validate independently → proper first-run onboarding
6. US5 → validate independently → `FLAG_SECURE` robustness
7. Polish → test coverage, CI, docs

### Parallel Team Strategy

With multiple developers, after Foundational completes:
- Developer A: US1 (core flow)
- Developer B: US4 (onboarding) — no file overlap with US1
- Once US1 lands: Developer A moves to US2 (extends US1's repository), Developer B
  moves to US5 (extends US1's detector + US4's accessibility service)
- US3 is best taken by whoever finishes US1/US2 first, since it extends both

---

## Notes

- `[P]` tasks touch different files with no unmet dependency
- `[Story]` labels map every story-phase task to its spec.md user story for
  traceability back to FR-XXX requirements
- Commit after each task, using Conventional Commits, per constitution's Development
  Workflow & Quality Gates section
- Stop at any checkpoint to validate a story independently before continuing
- T001, T009, T030/T066/T067/T068, and T079 directly fulfill this round's explicit
  task-generation requirements: dependency-version pinning as the first task,
  ambiguous-case `BlackFrameDetector` testing, the external trigger contract
  definition, and the CI build workflow
