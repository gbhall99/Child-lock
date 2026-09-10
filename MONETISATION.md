# Monetising Child Lock

## The shape of the market

Child Lock solves a sharp, recurring problem for one clear audience: parents
of toddlers who video-call grandparents. It is a utility people would happily
pay a few pounds for once, not a service they will pay for monthly. Design the
model around that: a generous free tier that proves it works, and a one-time
"Pro" unlock for the convenience features. Subscriptions for a utility this
small cause refunds and one-star reviews.

## Freemium split

| Free (proves the value) | Pro, one-time purchase |
|---|---|
| Touch lock with the corner-hold unlock | Volume-button pattern (lock and unlock without touching) |
| Quick Settings tile and in-app arming | Volume chord |
| Badge in a fixed corner | Swipe blocking (home/back gestures stopped outright) |
| Setup assistant | Badge-then-PIN unlock |
| | Badge position, size and colour |
| | Auto-lock when a chosen app opens (a natural next feature) |

Price: £2.99, matching `FeatureGate` in the code. (An earlier draft suggested £3.99 with a £2.99 introduction; the single price is simpler and is what the code and copy now say.) Pro is a
single non-consumable product in Play Billing, restored automatically on
reinstall. Offer a "Family" variant later only if data shows shared devices.

The gate already exists in code: `billing/FeatureGate.kt`. Every Pro feature
asks it once; wiring Play Billing means replacing its `isPro()` body with the
cached purchase state and adding one paywall screen. Nothing else changes.

## Why the Play Store, not sideloading

Play distribution is not only about reach:

- Play-installed apps are not "sideloaded", so the restricted-settings dance
  for the overlay and accessibility permissions disappears. Setup becomes
  three taps.
- Play Billing is the only sane way to sell to Android users.
- Automatic updates.

Two policy items need care before submission:

1. **Accessibility service.** Google requires a declaration for
   non-accessibility use of `AccessibilityService`. Child Lock's use is
   legitimate parental control, but the listing and the in-app prominent
   disclosure must say exactly what the service does (reads key presses,
   blocks navigation while locked) and that it reads no screen content. Keep
   the free tier fully functional without the service so reviewers see it as
   optional hardening, which it is.
2. **Display over other apps.** Allowed; explain it in the listing.

## Launch plan

1. Rename the package to something you own (`com.gbhall.childlock` is fine if
   you hold the domain or accept it as the identifier forever).
2. Create the Play Console app, one non-consumable product `pro_lifetime`.
3. Add Play Billing (AndroidX `billing-ktx`) behind `FeatureGate`; add a
   paywall card in "More options" and a lock icon next to Pro rows.
4. Store listing: 4 screenshots (locked call, volume gesture, setup assistant,
   settings), a 20-second video of the volume-button toggle. The hook is one
   sentence: "Hand your phone to your toddler on a video call. They can watch,
   tap all they like, nothing happens."
5. Closed test with 20 parents (Play requires 12 testers for 14 days for new
   personal accounts anyway). Collect device/skin data: Samsung and Xiaomi
   handle overlays and accessibility differently and are most of the market.
6. Price test after 30 days: £2.99 vs £4.99 on the same listing via Play
   price experiments.

## Realistic numbers

A niche utility with good ratings typically converts 3 to 6 percent of
installs to a one-time purchase. At 5,000 installs a month that is roughly
150 to 300 purchases, or about £400 to £900 a month after Google's 15 percent
cut. It grows with ratings and with the "grandparent" word of mouth, which
this app is unusually well placed for. Beyond that: a sibling app for tablets,
and licensing the volume-pattern toggle to bigger parental-control suites.

## Ads, usage limits and other models (considered and mostly rejected)

**Ads.** Ads pay per screen view and this app's screens are rarely viewed:
once at setup, a few times a year afterwards. The moment of real use is the
locked call, where the only thing on screen is a transparent overlay, so an
ad is impossible there. Ten thousand monthly users opening the app three
times a month is about thirty thousand banner views, or £15 to £60 a month
at typical utility rates, before adding the AdMob SDK, a consent dialog, a
privacy policy and a Families-policy review triggered by "child" in the name.
It also makes the app feel cheap. Not worth it.

**Usage limits.** A cap on locks fails at the worst possible moment (parent
on a call, child reaching for the phone, "limit reached"), earns refunds and
one-star reviews, and is bypassed by clearing app data. Rule: the lock itself
always works, unlimited, with no nagging. Charge only for things people are
glad exist, never for things they hit a wall on.

**Keep the volume pattern free.** It is the reason the app is good and the
reason parents will recommend it. Pro (£2.99 one-time) is everything
automatic: locking by itself when a call connects or a video goes full
screen, locking again after an unlock, and skipping ads. This is now wired
in code: `FeatureGate` gates those features, `Paywall` explains and will
start the purchase, and debuggable builds are Pro so the owner can test.
Only `FeatureGate.isPro()` needs Play Billing behind it.

**Tip jar.** Three "support Child Lock" purchases (£1.99, £4.99, £9.99) that
unlock nothing. Converts at roughly one to three percent of a passionate
audience and never degrades the app. Combines with Pro.

**Open source plus a paid Play build.** The code is public. "Free if you
build it yourself, £3 on Play for the convenience" is a respected model and
doubles as marketing to technical parents.

**Licensing.** Video-call apps have no toddler mode. The touch-freeze plus
volume-pattern toggle is a small, well-defined component to pitch to kids'
video-call apps, telehealth apps, or a phone maker's kids-mode team. Longest
shot, biggest payoff.

**Family gifting.** A "send to grandparents" purchase that unlocks Pro on
another phone via a code. Fits how this app spreads.

**Extra stores.** Samsung Galaxy Store and Amazon Appstore listings cost
nothing; Samsung is a large share of the target market.
