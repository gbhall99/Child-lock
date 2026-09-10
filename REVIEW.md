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

**C3. Switch Access users are cut off. Planned (P1).** The commonest switch
setup maps switches to the volume keys, which the helper consumes while
locked. Needs detection of services that filter key events, and an
alternative unlock offered.

**C4. Voice Access and Braille users have no route in or out. Planned (P1).**
The overlay publishes no accessibility node to activate. Needs a labelled,
focusable unlock node on the shield and real nodes for the PIN keys.

**C5. Our own screens fail several WCAG checks. Planned (P1).** Status chip
text measures 2.0:1 to 4.3:1 against its own tinted background; switch
tracks and thumbs fall below the 3:1 non-text requirement; almost no
interactive control carries a content description; revealing hidden options
destroys screen-reader focus; fixed pixel heights clip at large font sizes;
the segmented control conveys selection by colour alone.

---

## Group D — Truthfulness and compliance (P1, blocking release)

**D1. The consent dialog and privacy policy were contradicted by the code.
Fixed.** Both said the app never reads what is on screen, while the skip-ad
feature reads button labels. Consent taken on a false statement is not
consent. The disclosure, the policy and the listing now carry the same
single exception, and the policy has a dedicated section for it.

**D2. Skip-ad tapping should not ship on Play. Decision needed.** It is
interference with another app's monetisation under the Device and Network
Abuse policy, it conflicts with YouTube's terms, and it is the only feature
that makes the privacy claims complicated. The recommendation is to compile
it out of the Play variant entirely rather than hide it behind a flag, and
keep it in the GitHub build.

**D3. The paywall sells something that does not exist. Planned (P1).** It
offers an upgrade and then shows a toast saying purchases are not available,
and the price is hardcoded rather than read from the billing API. Either
wire billing or hide the paywall before shipping.

**D4. No trader identity, address, terms or withdrawal notice. Planned
(P1).** All are required to sell into the UK and EU, and Play blocks EEA
distribution without the trader declaration.

**D5. "No internet permission" stops being true when billing lands.**

**D6. Brand names in marketing copy** imply affiliation. Nominative use in
the internal package list is fine; the listing should say "works with
popular video and video-call apps".

**D7. The app must never imply it makes a phone child-safe.** It blocks
touches, filters no content, and fails open. A sentence saying so belongs in
the listing.

---

## Group E — Platform correctness and cost (P1)

**E1. Work on every accessibility event. Fixed.** The shade check and the
relaunch check ran on every event, including the content-change storm inside
a video app, on the same thread that must answer key events within 500 ms.
They now run only on window changes.

**E2. Screen bounds taken from the service's display metrics. Planned.**
Wrong in split screen, freeform, picture-in-picture, on foldable inner
displays and on tablets, so the full-screen trigger and the skip-ad size
check misjudge. Should use the window metrics.

**E3. Orientation pinning from a translucent overlay is unverified.**
WindowManager generally ignores orientation requests from non-opaque overlay
windows. Needs a device check.

**E4. Release CI gaps. Planned.** The version code is pinned at 1, so every
tag would ship the same build; a missing signing secret produces a silently
unsigned bundle; release shrinking and lint only run at tag time; the
settings enums are not kept by the shrinker, so obfuscation could silently
reset every setting.

**E5. Smaller items.** The setup guide relaunches on every resume while
required steps are missing; the launchable and home-package caches never
invalidate; the banner ignores display cutouts; the screen receiver is not
registered as not-exported.

---

## Group F — Product gaps (P2)

Ranked by value at the actual moment of use.

1. **A session timer that hands the phone back.** The most requested feature
   in every competitor's reviews. The 90-minute safety cap now exists as a
   floor; a user-visible timer is the real feature. Small.
2. **A rehearsal step at the end of setup.** A parent who has unlocked once
   will not panic later. Small, highest return per hour.
3. **The unlock instruction where it survives.** The banner vanishes; the
   notification should carry the gesture permanently.
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
- The PIN is one SHA-256 pass with an app-wide salt over a four to eight
  digit space, and the wrong-attempt counter resets when the overlay is
  rebuilt. Should be a per-install salt with a slow hash.
- A view-id helper for skip-ad matching is defined and never called, so a
  broader capability is requested than exercised.
- The setup script edits a secure settings list with substring matching and
  an unescaped substitution, which can corrupt a real screen reader's entry.
- The notifications permission can only be granted from the setup guide.
- Price differs between the monetisation document and the code.

---

## Positioning

Screen pinning locks a child into an app but leaves every touch live. Kids
Space and Family Link build a separate world on the child's own device.
Child Lock is the only thing that works on the parent's phone, on whatever
is already on screen, with no profile switch and no screen touch.
