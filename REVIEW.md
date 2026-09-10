# Child Lock — expert review

_September 2026, against the branch `claude/kid-safe-call-lock-tfdbl1`._

Five independent reviewers examined the code, the strings, the rendered
screens and the documents:

| Lens | Looked at |
|---|---|
| Mobile security and privacy engineer | Threat model, escape and lockout paths, what the helper can see |
| Principal Android engineer | Platform correctness on Android 8 to 16, OEM behaviour, races, cost |
| Digital accessibility specialist | WCAG on our own screens, and conflicts with assistive technology |
| App compliance and trust-and-safety consultant | Play policy, UK and EU consumer law, truthfulness of every claim |
| Product manager with support-operations background | Real moment of use, uninstall risks, support load |

Findings are grouped by theme rather than by reviewer, because the same
defects surfaced from several angles. **P0** ships-broken or unsafe, **P1**
before any public release, **P2** next, **P3** polish.

Items marked **Fixed** were corrected in the commit that adds this document.

---

## Group A0 — Trapped by the lock (P0, found in use)

**A0.1. The unlock depended on receiving the final key's release. Fixed.**
The rewritten volume pattern completed only when the last press was released
and had been held long enough. If a device never delivers the release of a key
the helper consumed, that completion never fires and the parent cannot get
out. Reproduced in a test, then fixed: the unlock now completes on the hold
itself, driven by the same tick the helper already runs, with the release kept
only as a fast path. The mashing simulation was re-run with ticks driven the
way the helper drives them, so completing on a hold has not opened a new way
in.

**A0.2. A practice lock could strand the parent. Fixed.** Trying the lock out
is exactly when a parent does not yet know how to escape it, so a practice
lock now always releases itself after a minute regardless of anything else.

**A0.3. The advertised backup unlock did not exist in the default setup.
Fixed.** The app said "holding two corners always works as a backup", but
blocking swipes uses the same mode a screen reader uses, and in that mode the
screen sends hover rather than touches, so the corner hold cannot fire. With
the volume pattern also failing, there was no way out at all. The app now
names the backup that can actually work in the current configuration, and
repeats it in the lock notification, which is the reminder that survives.

---

## Group A — The lock did not actually hold (P0)

**A1. Random volume mashing unlocked the phone in seconds. Fixed.**
The default pattern was two presses inside 900 ms with no dwell time, no
cooldown, and a recogniser that treated any wrong key as the start of a
fresh attempt. Simulating a child pressing the rocker every 150 to 700 ms
against the shipped recogniser:

| Mashing for | Old default (2 presses) | Old "press it twice" |
|---|---|---|
| 5 seconds | 99.6% unlocked | 44.6% |
| 15 seconds | 100% | 86.2% |
| 30 seconds | 100% | 98.5% |
| 60 seconds | 100% | 100% |

The recogniser now requires a clean run: any wrong press opens a quiet
period that further presses keep open, and the final press must be held
briefly. Measured over ten minutes of simulated mashing, 200 trials, it
never unlocks, while a deliberate adult still succeeds first time. This is
now a permanent test.

**A2. No maximum lock duration, and no check that the way out exists. Fixed.**
If an OEM battery manager stopped the helper mid-lock with a volume gesture
selected, nothing saw the keys, the shield still swallowed every touch, and
the corner-hold fallback did not work under touch exploration. Recovery was
a reboot. Two changes: the app now refuses to lock when the chosen way out
would not work, and no lock outlives 90 minutes.

**A3. A re-lock immediately after an unlock was silently undone. Fixed.**
Unlocking scheduled the service to stop once the "off" notice had been seen.
Locking again inside that window attached the overlay and then had it torn
straight back down, leaving touch live while the parent believed it was
locked. That is the single most common parent action. The pending stop is
now cancelled, gated on the state, and uses the start id.

---

## Group B — Safety and emergencies (P0)

**B1. An incoming call could not be answered. Fixed.**
The overlay sits above the dialer's in-call activity, so the swipe to answer
was swallowed, and emergency calls were equally blocked for anyone who did
not know the gesture. There was no telephony handling anywhere in the code,
and the README claimed the opposite. The lock now releases itself as soon as
the phone rings or a call connects, using the audio mode, which needs no
telephony permission.

**B2. Explore-by-touch could outlive the lock. Fixed.**
The flags were cleared only through a posted listener, never on unbind, and
the memo of what had been requested was written before the assignment that
could throw. A parent could be left in explore-by-touch, where the
touch-based unlocks no longer receive real touches. Flags are now cleared on
unbind, re-cleared on connect, and recorded only after they take effect.

**B3. The badge could be missing while locked. Fixed.**
The shield skipped drawing when layout had not yet produced a badge
position, contradicting the stated property that no badge means no lock. It
now falls back to a default position.

**B4. The helper fought the device lock screen. Fixed.**
Back and volume were consumed, and the notifications panel dismissed, even
on the keyguard, so a parent could not silence an alarm or use the PIN pad
after the screen went off and on. Both now stand down on the keyguard.

---

## Group C — Assistive technology (P0 and P1)

The app uses the same platform mechanism screen readers use, and had no
detection of them anywhere.

**C1. A screen-reader user choosing corner-hold or PIN could not unlock at
all. Fixed.** Touch exploration is already on system-wide for them, so those
unlocks never receive real touches, and the copy promised corner-hold
"always works as a backup". The app now refuses to lock in that combination
and explains why.

**C2. The helper no longer fights a screen reader. Fixed.** It stops
requesting explore-by-touch, and stops claiming the three-finger triple tap,
whenever another tool already owns them.

**C3. Switch Access users are cut off. Fixed.** The commonest switch setup
maps switches to the volume keys, which the helper consumed while locked. The
app now detects another tool filtering key events and refuses to lock with a
volume gesture, pointing the parent at a touch unlock instead.

**C4. Voice Access and Braille users had no route in or out. Fixed.** The
shield now publishes a focusable node that announces the lock and the way out,
and offers unlocking as a named custom action rather than a plain click, so a
child cannot stumble into it. The PIN keys carry real labels.

**C5. Our own screens failed several WCAG checks. Fixed.** Chips now use a
separate accessible text token rather than the tone colour on its own tint;
switch tracks and thumbs use opaque tokens that clear 3:1; switches, sliders
and expanders carry labels, states and roles; decorative icons are marked as
such; headings announce as headings; the segmented control announces as a
radio group instead of relying on colour; expanding a section moves focus and
announces itself; touch targets are 48dp; fixed heights became minimums so
large font sizes no longer clip.

---

## Group D — Truthfulness and compliance (P1, blocking release)

**D1. The consent dialog and privacy policy were contradicted by the code.
Fixed.** Both said the app never reads what is on screen, while the skip-ad
feature reads button labels. Consent taken on a false statement is not
consent. The disclosure, the policy and the listing now carry the same
single exception, and the policy has a dedicated section for it.

**D2. Skip-ad tapping does not ship on Play. Fixed.** The project now builds
two flavours. `sideload` keeps the feature; `play` is compiled without it, so
the matcher and every node-reading path are absent from the released binary
rather than hidden behind a flag. Verified by compiling the Play flavour and
confirming the matcher class does not exist in it.

**D3. The paywall no longer sells something that does not exist. Fixed.**
While billing is not wired, the sheet explains that the extras are unlocked
and nothing is for sale. A single flag turns the purchase path on, and the
price will come from the billing library rather than a constant.

**D4. No trader identity, address, terms or withdrawal notice. Documented,
yours to complete.** The listing and release checklist now spell out exactly
what is required: the Digital Services Act trader declaration, a terms URL
covering the 14-day right of withdrawal and refunds, and naming that trader
as data controller in the privacy policy.

**D5. "No internet permission" stops being true when billing lands.**

**D6. Brand names removed from marketing copy. Fixed.** Nominative use in the
internal package list stays; the listing no longer names other companies'
apps.

**D7. The app no longer implies it makes a phone child-safe. Fixed.** Both
the listing and the README now say plainly that it blocks touches, filters no
content, and is not a substitute for supervision.

---

## Group E — Platform correctness and cost (P1)

**E1. Work on every accessibility event. Fixed.** The shade check and the
relaunch check ran on every event, including the content-change storm inside
a video app, on the same thread that must answer key events within 500 ms.
They now run only on window changes.

**E2. Screen bounds taken from the service's display metrics. Fixed.** They
now come from the window metrics, which are correct in split screen,
freeform, picture-in-picture and on foldable inner displays.

**E3. Orientation pinning from a translucent overlay is unverified.**
WindowManager generally ignores orientation requests from non-opaque overlay
windows. Needs a device check.

**E4. Release CI gaps. Fixed.** The version code is derived from the commit
count at tag time; a missing signing secret now fails the job instead of
producing an unsigned bundle; release lint and both flavours build on every
pull request; the wrapper is validated; and the shrinker keeps the enum
constants that settings are persisted by.

**E5. Smaller items. Fixed.** The setup guide is offered once per visit
rather than on every resume; the package caches expire and are bounded; the
banner keeps clear of display cutouts; the screen receiver is registered as
not-exported.

---

## Group F — Product gaps (P2)

Ranked by value at the actual moment of use.

1. **A session timer that hands the phone back. Done.** Settable in five
   minute steps up to an hour, off by default, alongside the 90-minute cap.
2. **A rehearsal step. Done.** A "Practise" button locks the app's own screen
   so the parent can try unlocking before it matters.
3. **The unlock instruction where it survives. Already true**: the lock
   notification carries the gesture for as long as the lock is on.
4. **A deliberate way for the child to end a call.** During a grandparent
   call the child is the one who wants to wave goodbye. Medium.
5. **A tablet story.** The car and restaurant cases are tablets, and
   corner-to-corner holds do not scale to a ten-inch screen. Medium.

Not recommended: per-child profiles. The real variance is per-app.

---

## Group G — Uninstall risks and support load (P2)

1. **"It stopped working after a day."** OEM battery managers kill the
   helper. Needs a health check on open and a deep link to that
   manufacturer's battery settings.
2. **"I could not get past the greyed-out switch."** Show the
   restricted-settings walkthrough up front for sideloaded installs. Play
   distribution removes the problem entirely.
3. **"I panicked and could not unlock."** Covered by the timer, a permanent
   gesture reminder and the rehearsal step.

A restore-purchase path must exist before billing ships.

---

## Group H — Hygiene (P3)

- Seven dead strings removed. **Fixed.**
- The PIN is now PBKDF2 with 120,000 rounds and a random per-install salt,
  compared in constant time. **Fixed.** The wrong-attempt counter still
  resets when the overlay is rebuilt.
- A view-id helper for skip-ad matching is defined and never called, so a
  broader capability is requested than exercised.
- The setup script now edits that secure settings list element by element
  rather than by substring, so a real screen reader's entry cannot be
  corrupted. **Fixed.**
- The notifications permission is grantable from the main screen again.
  **Fixed.**
- Price is now one number across the code and the documents. **Fixed.**

---

## Positioning

Screen pinning locks a child into an app but leaves every touch live. Kids
Space and Family Link build a separate world on the child's own device.
Child Lock is the only thing that works on the parent's phone, on whatever
is already on screen, with no profile switch and no screen touch.
