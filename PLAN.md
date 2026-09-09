# Child Lock — design plan

An Android app that lets a parent hand their phone to a child during a video call.
While locked, the child can see and hear the call, but every touch is swallowed.
The screen is never covered or dimmed; the only visible sign is a small badge.
The parent unlocks with a gesture that is trivial for an adult and unlikely for a child.

## 1. Goals and non-goals

Goals
- Block all screen touches over any app (the video call app stays fully visible and running).
- Show only a small semi-transparent badge (about 28 dp) in a corner while locked.
- Unlock in under two seconds with a deliberate adult gesture. No PIN typing by default.
- Arm the lock from the call screen without leaving the call (Quick Settings tile).
- Never trap the parent: the lock must be recoverable even if something goes wrong.
- Personal, sideloaded app. No Play Store policy compliance work.

Non-goals (v1)
- Blocking the power button. Android does not let apps intercept it.
- Blocking the fingerprint sensor or screen-off behaviour.
- Parental controls beyond the single "freeze touches" mode.
- Supporting Android below 8.0 (API 26).

## 2. How touch blocking works on Android

The core mechanism is a full-screen, fully transparent overlay window drawn by a
foreground service using `WindowManager` with type `TYPE_APPLICATION_OVERLAY`.
This needs the "Display over other apps" permission (`SYSTEM_ALERT_WINDOW`).

Key window flags:
- `FLAG_NOT_FOCUSABLE`: the overlay never steals keyboard focus, so the call app
  keeps behaving normally.
- No `FLAG_NOT_TOUCHABLE`: the overlay receives every touch and consumes it.
- `FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS`: cover the full display
  including cutout and navigation bar areas.
- `FLAG_KEEP_SCREEN_ON`: optional, so the screen does not time out mid-call.

Android 12+ "untrusted touch" rules block touches that *pass through* an overlay
into another app. Our overlay consumes touches rather than passing them, so it is
unaffected. The badge is the only visible content in that window.

What an overlay cannot block, and the mitigation for each:

| Input | Overlay blocks it? | Mitigation |
|---|---|---|
| Screen touches | Yes | Core feature |
| Home / recents gesture or button | No | Accessibility service detects the call app leaving the foreground and relaunches it immediately |
| Back button (3-button nav) | No | Accessibility service filters `KEYCODE_BACK` |
| Back gesture (edge swipe) | Partially | Overlay excludes up to 200 dp per edge from system gestures; relaunch handles the rest |
| Notification shade / quick settings | No | Accessibility service dismisses the shade the moment it opens (API 31+); on older APIs it sends Back |
| Volume keys | No | Accessibility service filters `KEYCODE_VOLUME_UP/DOWN` |
| Power button | No | Accepted limitation. The call keeps running with the screen off; the parent unlocks the phone and the lock is still in place |
| Incoming phone call UI | No | The system in-call screen sits above overlays. Accepted |

The accessibility service is optional "hardening". The app works with the overlay
alone, and the setup screen explains what each extra permission adds.

## 3. Unlock gesture

Shipped default (added after hands-on use): **volume-button pattern**. A quick
volume up then down (or down then up, optionally twice) both locks and unlocks,
via the accessibility service, with no screen touching. The corner hold below
remains as the always-available fallback.

Original recommended default: **two-finger diagonal corner hold**.
- One finger held inside the top-left corner zone (about 15% of width and height).
- A second finger held inside the bottom-right corner zone at the same time.
- Both held for 1.5 s with no third finger down and no finger leaving its zone.
- The badge shows a progress ring during the hold, then a short haptic on unlock.

Why it works: it needs an adult-sized hand span or two hands used deliberately,
it survives random mashing (any extra finger resets the timer), and it takes no
UI that would cover the call. Zones are computed in overlay coordinates and
recomputed on rotation.

Alternatives, selectable in settings:
- **Badge long-press then PIN**: hold the badge for 1 s, a small 4-digit pad
  appears in that corner. Stronger, but briefly covers a small area of the call.
- **Volume chord**: hold volume up and volume down together for 1.5 s.
  Requires the accessibility service. Easiest one-handed option.

All gesture recognisers are pure Kotlin state machines fed by
`MotionEvent`/key events, so they can be unit tested without a device.

## 4. Arming the lock

- **Quick Settings tile** (primary): swipe down, tap "Child Lock", hand over.
  The tile shows locked/unlocked state.
- **In-app "Arm in 5 s" button**: opens the app, tap Arm, a countdown lets the
  parent switch back to the call. Fallback for phones that hide custom tiles.
- Both routes record the current foreground package as the "protected app" so
  the relaunch mitigation knows what to bring back.

## 5. Safety valves (never trap the parent)

- Lock state lives in memory only. It is never written to disk, so a reboot or
  process death always results in an unlocked phone.
- The foreground service's persistent notification cannot unlock (that would
  give the child a path via the shade) but it does show "Locked. Hold both
  corners to unlock" as a reminder.
- Long-pressing power to reboot is the universal escape hatch.
- If the overlay window fails to attach for any reason, the service stops and
  reports unlocked, rather than half-locking.
- The badge is always visible when locked. No badge means no lock.

## 6. Architecture

Single-module Kotlin app, minSdk 26, targetSdk 35, and **no third-party or
AndroidX dependencies**. The settings screen is built from framework widgets
and settings live in SharedPreferences. For an app whose whole job is to sit
over every other app, a dependency-free build is smaller, faster to audit, and
cannot break on a library update. (An earlier draft proposed Compose and
DataStore; they added nothing the screen needs.)

```
app/
  ui/
    MainActivity.kt          setup, permissions, gesture and hardening settings, Arm button
    Ui.kt                    small helpers for building the screen in code
  lock/
    LockController.kt        single source of truth for lock state, in-memory only
    LockOverlayService.kt    foreground service; creates/removes the overlay window
    OverlayRoot.kt           overlay root: touch shield plus optional PIN pad
    TouchShieldView.kt       transparent full-screen view that consumes touches, draws the badge
    PinPadView.kt            compact keypad for the badge-PIN gesture
  gesture/
    UnlockGesture.kt         interface plus shared hold timer; emits Progress/Unlocked/Reset/ShowPinPad
    CornerHoldGesture.kt     two-finger diagonal corner hold
    BadgePinGesture.kt       long-press badge, then PIN pad
    VolumeChordGesture.kt    volume up + down held (fed by the accessibility service)
    PinHasher.kt             salted SHA-256 for the stored PIN
  guard/
    GuardAccessibilityService.kt  key filtering, shade dismissal, foreground-app relaunch
    ForegroundTracker.kt          last foreground app, used as the app to protect
  tile/
    LockTileService.kt       Quick Settings tile (arms only, never unlocks)
  settings/
    LockSettings.kt          settings model and SharedPreferences repository
```

Runtime flow
1. Tile or Arm button calls `LockController.lock(protectedPackage)`.
2. `LockOverlayService` starts as a foreground service (type `specialUse`) and
   attaches `OverlayRoot` via `WindowManager`.
3. `TouchShieldView` forwards every `MotionEvent` to the active `UnlockGesture` and
   returns `true` so nothing reaches the app underneath.
4. On `Unlocked`, the controller flips state, the service removes the window and
   stops itself, and the tile updates.
5. If enabled, `GuardAccessibilityService` observes lock state and only acts
   while locked; when unlocked it is completely passive.

Permissions and manifest
- `SYSTEM_ALERT_WINDOW` (Display over other apps) — required.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`.
- `BIND_ACCESSIBILITY_SERVICE` on the guard service, with
  `canRequestFilterKeyEvents`, `canRetrieveWindowContent` and
  `flagRequestFilterKeyEvents`. Android 13+ requires the user to tap
  "Allow restricted settings" in App info before a sideloaded accessibility
  service can be enabled; the setup screen walks through this.
- `QUERY_ALL_PACKAGES` is not needed; the protected package comes from the
  accessibility window-change events or is simply omitted if the service is off.

## 7. Edge cases to handle

- Rotation and fold/unfold: overlay uses `MATCH_PARENT`; gesture zones recompute
  on layout change.
- Screen off then on: overlay persists; keyguard sits above it. After unlock the
  lock is still active.
- Call app enters picture-in-picture (child pressed home): relaunch brings it
  back full screen.
- Low memory kill: foreground service priority makes this rare; if it happens,
  the phone simply unlocks (badge disappears).
- Multi-window / split screen: overlay covers both panes; no special handling.
- Accessibility service disabled mid-lock: overlay keeps working; hardening
  silently degrades.
- Battery: overlay is static, gesture recognisers are event-driven, no polling.

## 8. Milestones

1. **Core lock** — project skeleton, overlay service, touch consumption, badge,
   corner-hold unlock, Arm-in-5s button, overlay permission prompt.
   Exit: any app can be locked and unlocked on a real device.
2. **Ergonomics** — Quick Settings tile, foreground notification, settings
   screen (gesture type, hold time, badge corner), permission setup checklist.
3. **Hardening** — accessibility service: back/volume filtering, shade
   dismissal, foreground-app relaunch; volume-chord gesture; setup guidance
   for restricted settings.
4. **Polish and tests** — progress ring and haptics, rotation handling, unit
   tests for all gesture state machines, crash-safety review, README with
   install and setup steps, signed release APK via Gradle.

## 9. Testing

- Unit tests (JVM): gesture recognisers with scripted event sequences,
  including "child mashing" sequences that must never unlock.
- Instrumented smoke test: service starts, window attaches, touches are consumed.
- Manual device matrix: gesture nav and 3-button nav, Android 12 and 14+,
  a real video call app (Google Meet, WhatsApp, FaceTime-style apps).

## 10. Open decisions for the owner

- Default unlock gesture: corner hold (recommended) vs badge-then-PIN.
- Whether the lock should also stop the child from ending the call. With the
  overlay it does, since the hang-up button is unreachable. If the child should
  be able to hang up, a small "pass-through hole" over that button could be
  added later, but it would be app-specific and fragile.
- Badge position default: top-left vs top-right (avoid the corner used by the
  call app's self-view thumbnail).
