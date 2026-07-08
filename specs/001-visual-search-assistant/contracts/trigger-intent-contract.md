# External Trigger Contract (T009)

This is the app's **one public interface** (plan.md, constitution). Everything a
third-party launcher (Square Home) or gesture app (Ubikitouch) needs to invoke a new
visual search is defined here. Implemented by T022 (`TriggerEntryActivity`).

## Why an Activity, not a Service/Receiver/ContentProvider

Third-party launchers and gesture apps universally know how to launch an **Activity**
— by explicit component, and often via a generic "pick any installed app's
activity/shortcut" picker UI. They do not have a standard, cross-app way to bind a
gesture to starting a `Service` or sending a targeted `Broadcast` to an arbitrary
package. An exported, no-UI Activity is therefore the lowest-common-denominator
mechanism virtually any such app can target, which is why the whole capture flow
starts here rather than at the `ScreenCaptureForegroundService` directly.

## Component identity (primary contract)

```text
Package:   com.circulesearch.app
Activity:  com.circulesearch.app.ui.trigger.TriggerEntryActivity
Exported:  true
Theme:     @style/Theme.CircleSearch.Transparent (no visible chrome)
Launch mode: singleTask (a second trigger while one is already resolving must not
             stack a second instance — see FR-018/FR-023 superseding behavior)
```

Any launcher/gesture app that can "launch an app's Activity by component" (Square
Home's Activities picker, or any generic Android activity picker) targets this
component directly via an explicit `Intent`:

```kotlin
Intent().apply {
    setClassName("com.circulesearch.app", "com.circulesearch.app.ui.trigger.TriggerEntryActivity")
}
```

This is the contract callers should treat as stable. No extras are required for this
path — a bare launch is a complete, valid invocation.

## Secondary contract: custom action (for intent-capable callers)

For gesture/automation apps that let the user configure a raw `Intent` (action +
category) rather than picking a component directly (e.g. Ubikitouch, if/when its
gesture-to-intent editor is used instead of an app/activity picker), the same
Activity also resolves via:

```xml
<intent-filter>
    <action android:name="com.circulesearch.app.action.START_VISUAL_SEARCH" />
    <category android:name="android.intent.category.DEFAULT" />
</intent-filter>
```

```kotlin
Intent("com.circulesearch.app.action.START_VISUAL_SEARCH")
```

## Extras

None are required or defined for v1. `TriggerEntryActivity` takes no input beyond
"start a new search now" — all real configuration (active profile, preferences) lives
in persisted Settings, not in the triggering Intent. Any extras present on the
incoming `Intent` are ignored, not validated (forward-compatible: a caller sending
unexpected extras must not break the trigger).

## Caller expectations (response contract)

- **Fire-and-forget**: the caller does not receive a result back (no
  `startActivityForResult`/Activity Result contract on the caller's side is required
  or supported). `TriggerEntryActivity` does not call `setResult()`.
- **No visible app switch from the caller's perspective**: `TriggerEntryActivity` is
  translucent and immediately hands off to the selection overlay
  (`SelectionOverlayWindow`, a `WindowManager` overlay, not a second Activity) — the
  caller's own foreground app (whatever the user was in when they triggered this)
  remains the visually foregrounded app throughout, per FR-002.
- **Preconditions are the app's own responsibility, not the caller's**: if permissions
  are missing or no AI Endpoint Profile is configured, `TriggerEntryActivity` handles
  that itself (routes to onboarding/Settings notices per FR-013/FR-017/FR-022) — the
  caller does not need to check any state before invoking the trigger.
- **Idempotent-ish under rapid re-invocation**: a second trigger while a search from
  the first is still in flight supersedes it (FR-018/FR-023) rather than erroring or
  stacking a second overlay.

## Verification caveat

Square Home and Ubikitouch are closed, third-party products with no public API
reference available to verify their exact picker/binding UX against. The component-name
contract above is the standard, most broadly-supported Android mechanism and is what
this app commits to as stable — but the specific step-by-step binding flow in each of
those two apps must be manually verified on a real device as part of
`quickstart.md`'s validation pass (T078), not assumed from this document alone.
