# Quickstart Validation Guide: Visual Search Assistant

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

Manual, end-to-end validation steps that prove the feature works on a real device.
This is not an automated test suite — see `tasks.md` (Phase 2) for the automated unit
and instrumentation test breakdown. Run this after each major implementation
milestone, and always before considering a release build ready.

## Prerequisites

- A physical Android device or emulator running Android 14+ (API 34+) to exercise the
  full foreground-service-type enforcement path described in research.md R1; a second
  run on API 26 (minSdk) and API 35 is recommended to catch version-specific
  regressions.
- A debug build of the app installed (`assembleDebug`, or the CI-produced artifact —
  see the project's GitHub Actions workflow).
- At least one reachable OpenAI-compatible endpoint to point a profile at. Any of the
  following work: a real OpenAI/OpenRouter API key, a local Ollama instance on the same
  network with a vision-capable model pulled, or a lightweight mock server that returns
  a fixed `/v1/chat/completions`-shaped JSON response (useful for offline validation of
  everything except real answer quality).
- A third-party launcher (Square Home) or gesture app (Ubikitouch) installed, configured
  to invoke the app's exported trigger entry point — or, as a substitute during
  development, `adb shell am start -n <package>/.ui.trigger.TriggerEntryActivity`.

## 1. Onboarding & permissions (User Story 4 / P3)

1. Fresh-install the app (`adb uninstall` first if re-testing) and launch it.
2. Confirm each of the three permissions (overlay, accessibility, screen capture
   capability) is explained in plain language before its corresponding system prompt
   appears.
3. Grant overlay and accessibility; leave the app and confirm re-opening it reflects
   updated onboarding progress.
4. Force-stop and relaunch: confirm onboarding is skipped/shown-complete since
   permissions already granted.

**Expected**: no permission is requested without a preceding explanation; the app
never silently proceeds past a missing permission.

## 2. BYOK profiles & fallback (User Story 2 / P2)

1. Open Settings → create a profile named "Primary" pointed at a reachable endpoint;
   run "test connection" → expect a clear success indicator.
2. Create a second profile "Backup" pointed at a different reachable endpoint; mark
   "Primary" active and set "Backup" as its fallback.
3. Create a third, deliberately broken profile "Primary" variant pointed at an
   unreachable host (e.g. `http://10.255.255.1`) — mark it active instead, keep
   "Backup" as its fallback.
4. From the trigger, perform a real search (see section 4). Expect: the request to the
   broken active profile fails, the app automatically retries against "Backup", and the
   result panel discreetly indicates "Backup" produced the answer.
5. Point every profile at unreachable hosts and repeat: expect one clear, aggregated
   error with a retry affordance — never an indefinite spinner.

## 3. Local-network / cleartext endpoint

1. Configure a profile with `baseUrl = http://192.168.x.x:11434` (a local Ollama
   instance's typical address) — plain HTTP, private IP.
2. Run "test connection" — expect success without any manual network security
   configuration by the tester (the app's bundled `networkSecurityConfig` must already
   permit cleartext to private ranges).
3. Configure a profile with a public `http://` (non-HTTPS) address — expect this to
   fail at the network layer (blocked cleartext), surfaced as a clear connection error,
   not a crash.

## 4. Core capture-to-result flow (User Story 1 / P1)

1. Open any other app (e.g. a browser showing an article with a photo).
2. Invoke the trigger (launcher shortcut, gesture app, or the `adb` command above).
3. **Expect**: a transparent selection overlay appears near-instantly, with the
   underlying app still visible through it (per research.md R1 — no system dialog yet
   at this point).
4. Draw a selection around part of the visible content and confirm it.
5. **Expect**: the Android system "Start recording or casting?" consent dialog appears
   now (not before) — accept it.
6. **Expect**: a loading state appears in the result panel almost immediately, followed
   by a readable answer (or a clear, specific error if the endpoint is unreachable).
7. Dismiss the result panel.
8. **Expect**: the browser is exactly as it was — same scroll position, same tab, no
   navigation away and back.

## 5. Follow-up chat (User Story 3 / P2)

1. Repeat steps 1–6 above to get an initial result.
2. Type a follow-up question in the same panel and send it.
3. **Expect**: no new capture/consent flow is triggered; the answer reflects the
   original selection and prior turns.
4. Send a second follow-up that depends on the first follow-up's answer (context
   chaining) — confirm it's coherent.
5. Dismiss the panel, then re-trigger a brand-new search.
6. **Expect**: the new result panel has no memory of the previous conversation (no
   leaked messages, no pre-filled input).

## 6. Cancel-without-searching path

1. Trigger the assistant, then dismiss the overlay without drawing a selection (or
   draw one and cancel before confirming).
2. **Expect**: no consent dialog appears, no network request is made, the original app
   is untouched.

## 7. Minimum selection size

1. Trigger the assistant and tap once (near-zero-area selection) instead of dragging.
2. **Expect**: the app prompts to redraw a larger selection rather than submitting.

## 8. Protected content (`FLAG_SECURE`) fallback (User Story 5 / P4)

1. With text fallback **enabled** in Settings, trigger the assistant over a screen
   known to set `FLAG_SECURE` (a banking app's sensitive screen, or a test activity in
   the app itself that sets the flag deliberately for QA).
2. **Expect**: the app detects the black/blank frame, automatically falls back to
   on-screen text extraction, and the result clearly states the image could not be
   captured.
3. Disable text fallback in Settings and repeat.
4. **Expect**: a clear "search could not be completed for this screen" message, no
   blank/broken panel.

## 9. Multi-window / foldable

1. On a device or emulator supporting split-screen, put the target app in one half of
   the screen and trigger the assistant.
2. **Expect**: the selection overlay and resulting crop are correctly bounded to the
   target app's actual on-screen region, not the full physical display.

## 10. Idle battery/background check (SC-006)

1. Grant all permissions, do not perform any search, and leave the device idle for
   several minutes with the app backgrounded.
2. Inspect battery/background-activity stats (e.g. `adb shell dumpsys battery` /
   Android's own battery usage screen) for the app.
3. **Expect**: no foreground service notification is present, no measurable continuous
   background activity — consistent with research.md R1's "consent + capture + teardown
   happens only inside a single trigger-to-result cycle" design.
