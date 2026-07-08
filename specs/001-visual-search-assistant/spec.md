# Feature Specification: Visual Search Assistant

**Feature Branch**: `001-visual-search-assistant`

**Created**: 2026-07-08

**Status**: Draft

**Input**: User description: "App: assistente de busca visual acionável de qualquer tela.
Fluxo: um gatilho externo (atalho/Activity exportada, chamável por launchers de
terceiros como Square Home e apps de gesto como Ubikitouch) dispara a captura; o app
tira um frame da tela atual, abre um overlay de seleção onde o usuário desenha um
recorte; o recorte é enviado ao endpoint de IA configurado pelo usuário; a resposta
aparece num painel sobre o app em que o usuário estava, sem tirá-lo de contexto, e ao
fechar o painel o usuário volta exatamente para onde estava. Telas: onboarding de
permissões, configurações BYOK (Base URL, API Key, modelo, teste de conexão, fallback
por texto, qualidade de compressão), overlay de captura/recorte, e painel de
resultado."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Circle Anything, Anywhere (Priority: P1)

A user is in any app on their phone (a browser, a chat app, a photo gallery) and sees
something they want to know more about. Without switching apps or losing their place,
they invoke the assistant through an external trigger (a launcher shortcut or a gesture
mapped in a third-party gesture app), draw a rough selection around the item of
interest directly on top of whatever they were looking at, and receive an answer about
that selection moments later — then return to exactly where they were.

**Why this priority**: This is the entire value proposition of the product. Without
this flow working end-to-end, there is no product — every other screen exists to
configure or support this one journey.

**Independent Test**: Can be fully tested by configuring a valid AI endpoint, invoking
the trigger from any foreground app, drawing a selection around an on-screen element,
and confirming an answer is displayed without the originating app losing its state
(scroll position, form input, playback position, etc.).

**Acceptance Scenarios**:

1. **Given** the user is viewing content in any app and the assistant is fully
   configured and permitted, **When** the user activates the external trigger, **Then**
   a selection overlay appears on top of the current screen within a perceptible instant,
   without navigating away from the app underneath.
2. **Given** the selection overlay is visible, **When** the user draws a freeform or
   rectangular region around part of the screen, **Then** only the selected region is
   used as the basis for the search.
3. **Given** a valid selection has been made, **When** the selection is confirmed,
   **Then** the app sends the selection for analysis and displays a loading state
   immediately, followed by a readable answer or a clear error.
4. **Given** the answer panel is showing a result, **When** the user dismisses it,
   **Then** the app returns focus to the original app in the exact state it was in
   before the trigger was activated (no lost scroll position, input, or navigation
   state).
5. **Given** the user activates the trigger, **When** they decide not to search,
   **Then** they can dismiss the selection overlay without sending anything, returning
   cleanly to the original app.

---

### User Story 2 - Bring Your Own AI Endpoint, With Automatic Fallback (Priority: P2)

A user wants to control exactly which AI provider sees the content they search for, and
what it costs them — and wants resilience if their primary choice is temporarily
unavailable. They open Settings and create one or more named connection profiles (for
example, "OpenAI", "Home Ollama", "OpenRouter Backup"), each with its own connection
address, credential, and model identifier. They mark one profile as active and,
optionally, arrange the others into a fallback order. From then on, every search uses
the active profile — and if it fails, the app tries the next profile in the fallback
order automatically.

**Why this priority**: The core search flow (P1) has no destination for its requests
without a configured, working endpoint. This story is what makes P1 usable and
resilient, and it is the product's core trust model (no proprietary backend/data
collection, full user control over provider choice).

**Independent Test**: Can be fully tested by creating multiple named profiles in
Settings, marking one active and defining a fallback order, using "test connection" on
each, and confirming clear success or failure feedback per profile — independent of
ever performing an actual visual search.

**Acceptance Scenarios**:

1. **Given** the user is on the Settings screen, **When** they create a new named
   profile with a connection address, credential, and model identifier, **Then** the
   profile is saved and appears in their list of profiles.
2. **Given** the user has multiple saved profiles, **When** they mark a different one
   as active, **Then** all subsequent searches use that profile until changed again.
3. **Given** the user has more than one profile, **When** they arrange a fallback
   order, **Then** that order is saved and used if the active profile's request fails.
4. **Given** a profile's connection details, **When** the user requests a connection
   test for that profile, **Then** the app reports clearly whether it is reachable and
   correctly configured, without silently failing or hanging indefinitely.
5. **Given** invalid or incomplete connection details for a profile, **When** the user
   tries to save or test it, **Then** the app explains what is missing or wrong.
6. **Given** the user has previously configured profiles, **When** they reopen
   Settings, **Then** each profile's name, connection address, and model identifier are
   shown (the credential itself is never displayed in plain text).
7. **Given** the active profile's request fails during a real search, **When** a
   fallback profile is defined, **Then** the app automatically retries with the next
   profile in the fallback order and, if it succeeds, discreetly indicates which
   profile actually produced the result.
8. **Given** the user adjusts the compression quality or the text-fallback toggle,
   **When** they leave the Settings screen, **Then** the new preference is applied to
   all subsequent searches.

---

### User Story 3 - Continue the Conversation on a Search (Priority: P2)

After receiving an initial answer about something they circled, a user often has a
natural follow-up question — "what brand is that", "is this safe to eat", "translate
that line". Instead of starting an entirely new search, the user can keep typing in the
same result panel and get answers that understand what was originally circled and what
has already been discussed in that panel, for as long as the panel stays open.

**Why this priority**: A single, isolated answer is frequently not enough to satisfy
the user's actual need; the ability to refine or dig deeper in place is close to the
core value proposition itself, on par with configuring a working endpoint — without it,
users are forced to restart the whole trigger-and-select flow for every small
follow-up.

**Independent Test**: Can be fully tested by completing one visual search, then sending
one or more follow-up text messages in the same result panel, and confirming each
response accounts for the original selection and the prior messages — then closing the
panel and reopening the assistant to confirm no memory of that conversation remains.

**Acceptance Scenarios**:

1. **Given** an initial search result is displayed, **When** the user types and sends a
   follow-up message, **Then** the app shows the new message and a corresponding answer
   in the same panel, without re-triggering the capture/selection flow.
2. **Given** a follow-up has already been answered, **When** the user sends another
   follow-up, **Then** the answer takes into account both the original selection and
   every prior message in that session.
3. **Given** an ongoing conversation in the result panel, **When** the user views the
   panel, **Then** they see a running list of the session's messages (the initial
   result plus every follow-up exchanged) with a clear way to enter the next message.
4. **Given** an open result panel with an ongoing conversation, **When** the user
   dismisses the panel, **Then** the entire conversation is discarded immediately and
   nothing about it is written to persistent storage.
5. **Given** a conversation was previously discarded by closing the panel, **When** the
   user starts a brand-new visual search, **Then** the new result panel opens with no
   memory of any earlier conversation.

---

### User Story 4 - Understand and Grant the Right Permissions (Priority: P3)

A first-time user installs the app and needs to understand why it requires
screen-overlay, accessibility, and screen-capture permissions before any of the core
flow can work, and grant them with confidence.

**Why this priority**: The app is entirely non-functional without these permissions,
but they are sensitive system permissions users are conditioned to be wary of — clear
onboarding directly affects whether the app is usable at all, but it is only needed
once per install, making it lower priority than the recurring flows above.

**Independent Test**: Can be fully tested by installing the app fresh and walking
through onboarding, confirming each permission request is preceded by a plain-language
explanation of why it's needed, and that the app correctly detects when a permission has
been granted or denied.

**Acceptance Scenarios**:

1. **Given** a first launch with no permissions granted, **When** the user reaches
   onboarding, **Then** each required permission (overlay, accessibility, screen
   capture) is explained in plain language before the system prompt for it appears.
2. **Given** the user grants a permission, **When** they return to the app, **Then**
   onboarding reflects the updated status and advances to the next step.
3. **Given** the user denies or skips a required permission, **When** they attempt to
   use the core search flow, **Then** the app explains which permission is missing and
   how to grant it, rather than failing without explanation.
4. **Given** all required permissions are already granted (e.g., reinstall), **When**
   the user opens the app, **Then** onboarding is skipped or shown as already complete.

---

### User Story 5 - Reliable Results Despite Protected Content (Priority: P4)

A user circles something on a screen that the operating system prevents from being
captured as an image (for example, a banking app or a video streaming app marked as
protected content). Instead of receiving a blank or broken result, the user still gets
a useful answer based on the visible on-screen text, along with a clear notice that the
image itself could not be captured.

**Why this priority**: This is a robustness/edge-case story rather than a primary path
— most content the user circles will be capturable normally — but it prevents a
confusing silent failure in a scenario that will occur regularly for real users (e.g.
they can't detect it happening in advance).

**Independent Test**: Can be fully tested by triggering the assistant over an app or
screen known to block screen capture, and confirming the app surfaces a fallback
result derived from on-screen text plus a visible explanation, rather than an empty or
broken response.

**Acceptance Scenarios**:

1. **Given** the current screen blocks capture, **When** the user completes a
   selection, **Then** the app detects the blocked/blank capture and automatically
   falls back to extracting on-screen text instead of failing.
2. **Given** the fallback path was used, **When** the result is shown, **Then** the
   user is clearly informed that the image could not be captured and the answer is
   based on text only.
3. **Given** the text-fallback preference is disabled in Settings, **When** capture is
   blocked, **Then** the app informs the user the search cannot be completed for this
   screen, instead of silently failing or guessing.

### Edge Cases

- What happens when the external trigger fires but no AI endpoint profile has been
  configured and set active yet? The app must redirect the user to Settings with an
  explanation rather than attempting a request.
- What happens when the user draws a selection with a zero or near-zero area (a tap
  instead of a drag)? The app must require a minimum selection size and prompt the
  user to try again rather than sending an empty or near-empty image.
- What happens when the trigger fires again while a previous search is still in
  progress? The app must handle this predictably (e.g., cancel the previous search in
  favor of the new one, or block a new trigger until the current one resolves) rather
  than allowing overlapping, conflicting results.
- What happens when network connectivity is lost mid-search? The user must see a clear
  error with the option to retry, not an indefinite loading state.
- What happens when the active profile returns a response that isn't a valid answer
  (malformed data, unexpected format)? This is treated the same as any other request
  failure for fallback purposes — the app attempts the next profile in the fallback
  order, if any, before showing the user an error.
- What happens when the active profile and every profile in its fallback order fail
  (any combination of network error, timeout, HTTP error response, or malformed
  response)? The app must show one clear, aggregated error with a retry option rather
  than retrying indefinitely or failing silently.
- What happens when the user has not defined a fallback order, or has only one profile
  configured? A failure of the active profile must go straight to a clear error, with
  no fallback attempted.
- What happens if the user dismisses the result panel while a follow-up message is
  still in flight? The in-flight request must be canceled along with the rest of the
  conversation context — no orphaned response should appear after the panel is gone.
- What happens on foldable devices or in split-screen/multi-window mode where the
  visible app occupies only part of the physical screen? The selection overlay and
  resulting search must operate correctly against the visible app's region.
- What happens if the device screen is off, locked, or the trigger is invoked from a
  context where there is no meaningful foreground content to search? The app must
  decline gracefully with an explanation rather than capturing an invalid frame.
- What happens if the user revokes a previously granted permission (overlay,
  accessibility, or capture) after initial setup? The next attempted search must detect
  this and route the user back to the relevant explanation/grant step.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST expose an externally invokable entry point (independent
  of the app's own UI) that a third-party launcher or gesture-mapping app can call to
  start a new visual search, from within any other foreground app.
- **FR-002**: Upon being triggered, the system MUST capture the current on-screen
  content as the basis for selection, without requiring the user to first switch into
  the assistant's own app.
- **FR-003**: The system MUST present a selection overlay directly on top of the
  captured screen, allowing the user to draw a region (freeform or rectangular)
  identifying the area they want to search.
- **FR-004**: The system MUST allow the user to cancel the selection overlay at any
  point without submitting a search, returning them to the original app unchanged.
- **FR-005**: Once a selection is confirmed, the system MUST send only the selected
  region's content to the active AI endpoint profile for analysis.
- **FR-006**: The system MUST display the outcome of a search (loading, success, or
  error) in a result panel presented above the app the user was already using, without
  navigating the user away from that app. The same panel MUST serve as the surface for
  any follow-up conversation once an initial result is available.
- **FR-007**: When the user dismisses the result panel, the system MUST return the user
  to the exact state of the app they were previously using (same screen, scroll
  position, and input state).
- **FR-008**: The system MUST allow the user to create, name, edit, and delete multiple
  AI Endpoint Profiles, each capturing a connection address, access credential, and
  model identifier; no default or built-in AI backend is provided by the system itself.
- **FR-009**: The system MUST allow the user to designate exactly one AI Endpoint
  Profile as active at any time, and MUST use only the active profile to start a new
  search unless the automatic fallback behavior (FR-014) applies.
- **FR-010**: The system MUST allow the user to test the connection of any individual
  profile on demand and receive an explicit success or failure outcome for that
  profile.
- **FR-011**: The system MUST persist the user's full set of AI Endpoint Profiles, which
  one is active, the user-defined fallback order, and search preferences (including a
  text-only fallback toggle and a compression/quality preference) across app restarts.
- **FR-012**: The system MUST never display any stored access credential in plain text
  after it has been saved, for any profile.
- **FR-013**: The system MUST allow the user to define an ordered fallback sequence
  among their saved profiles, to be used if the active profile's request fails.
- **FR-014**: When a request to the active profile fails — due to a network error,
  timeout, HTTP error response, or a malformed/unparseable response — the system MUST
  automatically retry the same search using the next profile in the user-defined
  fallback order, if one exists, before surfacing an error to the user.
- **FR-015**: When a profile other than the originally active one produces the
  successful result for a search, the system MUST discreetly indicate to the user which
  profile actually answered.
- **FR-016**: If the active profile and every profile in the fallback order fail, the
  system MUST present one clear, aggregated error to the user rather than retrying
  indefinitely or failing silently.
- **FR-017**: The system MUST guide first-time users through explaining and requesting
  each permission required for the core flow (screen overlay, accessibility service,
  screen capture) before the core flow is used for the first time.
- **FR-018**: The system MUST detect, at time of use, whether a required permission is
  missing or has been revoked, and MUST direct the user to resolve it with a clear
  explanation, rather than failing without explanation.
- **FR-019**: When the current screen cannot be captured as an image (e.g., due to
  system-level content protection), the system MUST detect this condition and, if the
  user has enabled the text-fallback preference, MUST attempt to derive a result from
  on-screen text instead, informing the user this fallback was used.
- **FR-020**: When image capture is blocked and text fallback is disabled or
  unavailable, the system MUST inform the user the search could not be completed for
  that screen, rather than showing a blank or broken result.
- **FR-021**: The system MUST require a minimum selection area before allowing a search
  to be submitted, and MUST prompt the user to redraw the selection if it is too small.
- **FR-022**: If the trigger is invoked before any AI Endpoint Profile has been created
  and set active, the system MUST inform the user and direct them to complete
  configuration instead of attempting a search.
- **FR-023**: The system MUST handle a new trigger invocation that occurs while a prior
  search is still in progress in a well-defined way (e.g. superseding the prior search)
  rather than producing overlapping or conflicting results.
- **FR-024**: The system MUST present a clear, user-readable error with a retry option
  whenever a search ultimately fails (after any fallback attempts are exhausted) or
  times out, rather than leaving the user in an indefinite loading state or a silent
  failure.
- **FR-025**: The system MUST correctly determine the boundaries of the foreground
  app's visible content when capturing and mapping the user's selection, including when
  the foreground app occupies only part of the screen (multi-window/split-screen) or
  the device is a foldable in an intermediate posture.
- **FR-026**: After an initial search result is shown, the system MUST allow the user
  to send follow-up messages within the same result panel, without leaving the app they
  were using or re-triggering a new capture/selection flow.
- **FR-027**: Each follow-up message MUST be answered using the context of the original
  visual selection and every prior message exchanged earlier in that same session.
- **FR-028**: The system MUST hold the conversation context (the original selection and
  all exchanged messages) only in memory for the lifetime of the result panel; this
  context MUST NOT be written to persistent storage at any point.
- **FR-029**: When the result panel is dismissed, the system MUST discard the entire
  conversation context and message history immediately; reopening the assistant MUST
  start a new, empty session with no memory of the previous one.
- **FR-030**: The result panel MUST present the conversation as a running list of
  messages (the initial result plus any subsequent follow-up turns) together with an
  input affordance for the user to continue the conversation.

### Key Entities

- **AI Endpoint Profile**: Represents a single named BYOK connection — name, connection
  address, access credential, model identifier, and connection status. The user
  maintains a collection of these profiles; exactly one is marked active at a time, and
  the remaining profiles may be arranged into an ordered fallback sequence used
  automatically when the active profile's request fails.
- **Search Preferences**: User-adjustable settings governing search behavior — text
  fallback enabled/disabled, and image compression/quality level.
- **Visual Search Request**: Represents a single invocation of the core flow — the
  selected screen region, the point in time it was captured, and its resulting status
  (pending, succeeded, failed).
- **Conversation Session**: Represents the ephemeral, in-memory exchange tied to a
  single Visual Search Request — the initial Search Result plus any follow-up messages
  and their answers, in order. Exists only for the lifetime of the result panel;
  discarded in full, with nothing persisted, the moment the panel closes.
- **Search Result**: The answer content for a single turn within a Conversation Session
  (the initial answer or a follow-up answer), along with whether it was derived from
  image analysis or the on-screen text fallback, and which AI Endpoint Profile produced
  it.
- **Permission Status**: Tracks, per required system permission, whether it has been
  explained to the user and whether it is currently granted.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can go from activating the external trigger in any other app to
  seeing a selection overlay ready to draw on in under 1 second, perceived as
  instantaneous.
- **SC-002**: A user can complete a full visual search — trigger, select, and receive a
  result — without the originating app ever being backgrounded, closed, or losing its
  navigation/scroll/input state, in 100% of successful search attempts.
- **SC-003**: At least 95% of searches against a correctly configured, reachable AI
  endpoint result in either a displayed answer or a clear, specific error message
  within a reasonable wait time — never an indefinite loading state.
- **SC-004**: 100% of searches performed on screens that block image capture either
  produce a text-fallback answer with a visible notice, or a clear explanation that the
  search could not be completed — never a blank or broken panel.
- **SC-005**: A new user can complete permission onboarding and configure a working AI
  endpoint without external help, on their first attempt, for at least 90% of users in
  usability testing.
- **SC-006**: The app consumes negligible battery and system resources while idle
  (between searches) — no measurable continuous background activity is attributable to
  the app between one trigger invocation and the next.
- **SC-007**: A user can determine, within 2 actions from Settings, whether their
  configured AI endpoint is currently reachable and correctly set up.
- **SC-008**: When the active AI endpoint profile is unreachable or fails, at least 95%
  of searches with a configured fallback order still complete successfully via an
  automatically attempted fallback profile.
- **SC-009**: A user can send a follow-up message and receive a contextually relevant
  answer in the same result panel in 100% of attempts made before the panel is closed,
  with zero conversation data recoverable once the panel has been dismissed.

## Assumptions

- The app itself does not need to be the foreground/launched app for the trigger to
  work — the trigger is reachable while the app is backgrounded or not recently opened,
  as long as its background components (service/trigger receiver) are alive per
  standard Android lifecycle rules.
- No search history or persistence of past conversations is required for this version
  of the product; each Visual Search Request and its Conversation Session (initial
  result plus any follow-ups) are ephemeral and exist only in memory for as long as the
  result panel is open.
- Multiple named AI Endpoint Profiles are supported; exactly one is active at a time,
  selectable by the user, with the remaining profiles available as an optional
  user-defined fallback order.
- Because the configured AI endpoint is treated as stateless between requests, each
  follow-up turn in a Conversation Session resends the necessary prior context
  (previous messages and, depending on the endpoint's requirements, the original image
  selection) to the endpoint; this increases token usage per turn as a conversation
  grows, which is an accepted tradeoff for keeping all context client-side and
  unpersisted.
- The selection overlay operates on a single captured frame per invocation; the user
  does not need to capture and combine multiple frames in one search.
- "Reasonable wait time" for success criteria SC-003 is interpreted as being bounded by
  a generous, user-visible timeout rather than a specific fixed number of seconds,
  since response time is inherently dependent on the user's own chosen AI endpoint and
  network conditions (self-hosted/local models may be slower than commercial APIs).
- The primary language for all in-app explanatory text (onboarding, settings, errors)
  is not specified and defaults to matching the device's system language where
  supported, falling back to a single default language otherwise.
- Users are expected to already possess valid credentials/access for whichever AI
  endpoint they choose to configure; the app is not responsible for provisioning or
  billing for that access.
