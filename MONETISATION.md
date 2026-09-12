# Monetising Child Lock

## The shape of the market

Child Lock solves a sharp, recurring problem for one clear audience: parents
of toddlers who video-call grandparents. It is a utility people would happily
pay a few pounds for once, not a service they will pay for monthly. Design the
model around that: a generous free tier that proves it works, and a one-time
"Pro" unlock for the convenience features. Subscriptions for a utility this
small cause refunds and one-star reviews.

## The model: 30 days free, then bought once

Everything works for 30 days from first launch. After that the lock will not
start until the app is bought: one non-consumable product, `childlock_full`,
restored from Google Play on every launch and on any phone with the same
account. No subscription, no tiers, no feature matrix to explain.

Why whole-app rather than a free tier plus Pro extras: the app's value is
the lock itself, and a parent knows within a week whether it earns its place.
A month proves it on real calls and real tantrums; the purchase is then the
price of keeping something that already works. There is nothing to upsell
and nothing to nag about.

Price: base £2.99 in Play Console, Play converts the rest. The app shows the
price Play reports, so it can be changed in the console without a release;
a £2.99 vs £3.99 test after the first thousand installs is worth running.
Above £4.99 refund requests and one-star reviews rise sharply for a utility.

The mechanics, and their honest limits:

- Play has no free trial for one-time products (only subscriptions), so the
  trial clock is the app's own. First launch is stored on the device. Clearing
  the app's data restarts the trial; accepted, because the alternative is an
  account or a server, and the purchase itself can never be lost that way.
- Unlocking is never gated. A trial that ends while the phone is locked still
  lets the parent out; only starting a new lock needs the purchase. Practise
  keeps working after the trial so the parent can still see what they would
  be buying.
- The sideload flavour has no store and is simply unlocked: "free if you
  build it yourself, £3 on Play for the convenience".
- Selling makes you a trader in the EEA (Digital Services Act): name,
  address, phone and email go on the listing, and terms must cover the 14-day
  right of withdrawal.

The gate is one object, `billing/FeatureGate`, consulted by every way of
starting a lock (the button, the volume pattern, the tile, auto-lock, and the
overlay service as the backstop). `billing/PurchaseBackend` is the store
behind it: Play Billing in the play flavour, nothing in sideload, a fake in
tests.

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
2. Create the Play Console app and the one non-consumable product
   `childlock_full` (see RELEASING.md); add licence testers.
3. Play Billing is wired behind `FeatureGate`; the purchase state and the
   Restore button live in About.
4. Store listing: 4 screenshots (locked call, volume gesture, setup assistant,
   settings), a 20-second video of the volume-button toggle. The hook is one
   sentence: "Hand your phone to your toddler on a video call. They can watch,
   tap all they like, nothing happens."
5. Closed test with 20 parents (Play requires 12 testers for 14 days for new
   personal accounts anyway). Collect device/skin data: Samsung and Xiaomi
   handle overlays and accessibility differently and are most of the market.
6. Price test after the first thousand installs: £2.99 vs £3.99 via Play
   price experiments; the app needs no release for it.

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

**A free tier plus Pro extras.** The earlier plan: the lock free forever,
the automatic features paid. Rejected in favour of the trial because the
lock is the product; a free lock leaves nothing most parents would pay for,
and a feature matrix is one more thing to explain on a screen nobody reads.

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
