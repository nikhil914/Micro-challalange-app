# MicroMind roadmap

Goal: open the app, play one 10-second challenge, see your score, close the app. Under 20 seconds end to end.

## Audience and tone
- Teens, young adults and adults (13+). Not a kids' app.
- Feels like a game: dark arcade/neon theme, XP, 6 ranks (Rookie to Legend), "hot run" combo for back-to-back scores of 700+, short punchy copy ("Clutch", "Run it back").
- Play Console: set target age groups to 13-15, 16-17 and 18+. Do not join the Families program. Fill in the IARC questionnaire; expect a rating of Everyone or Teen.
- Store art and icon must not appeal mainly to children: no cartoon mascots or nursery colors. Otherwise Google may treat the app as child-directed (Families policy, COPPA).
- No ads, accounts or data collection in the MVP, which keeps the Data safety form simple. If ads come later, they must use ad SDKs certified for teen audiences.

## Phase 0: foundation (done in this repo)
- Gradle multi-module: `:core` (pure Kotlin, JVM-tested) and `:app` (Jetpack Compose).
- Challenge contract: deterministic session state machines, normalised 0..1000 score.
- Monotonic, pausable clock, so backgrounding never steals time or skews reaction times.
- TinyDB-style key/value store (`KeyValueStore`), backed by SharedPreferences on Android.
- Adaptive difficulty per game: a staircase that targets ~70% performance.
- Streaks with auto "freeze" (one per 7 days, max 2), personal bests, history (last 60 plays).
- Picker: no repeat of the last 2 types, favours types played least.
- Daily challenge: same game and seed for everyone each day, fixed level 5.

## Phase 1: MVP (v0.1)
- 8 games: Reaction Tap, Number Memory, Pattern Recall, Color Clash (Stroop), Math Sprint, Odd One Out, Go/No-Go, Bigger Number.
- Every open gives a new challenge: intro, 3-2-1, 10 s play, result.
- Result: animated score, best, streak, level. Stats: per-game best, average, level, trend line.
- Settings: haptics, reset progress. App shortcuts: Daily, Stats.
- Exit criteria: core tests green, app builds, manual test on 1 low-end and 1 flagship device.

## Phase 2: polish (v0.2)
- Sound effects (SoundPool, off by default) and a "reduced motion" mode.
- Onboarding: one 3-screen intro and a baseline "first 3 challenges" flow.
- Glance home-screen widget: streak + "Play today's challenge" button.
- Opt-in daily reminder notification (WorkManager, user-chosen time; POST_NOTIFICATIONS on API 33+).
- Migrate the store to Preferences DataStore, behind the same `KeyValueStore` interface.
- Localised strings; TalkBack audit; large-font layouts.

## Phase 3: depth (v0.3)
- 4 more games, from research: Flanker (color-free inhibition task), Chimp Order, Rule Switch, Lights-Out mini. Flanker and Chimp Order come first.
- "Shape Clash" variant of Color Clash, for color-blind users.
- Leave reaction games out of the rotation when TalkBack touch exploration is on.
- Weekly "brain profile" card per skill (Memory, Speed, Attention, Logic, Math): relative to your own past only.
- Achievements and badges (first 1000, 7-day streak, all games played, reach Elite).
- Weekly ranked season for the daily challenge, with rank-up animations.
- Room database for full history, CSV export. Store the device model with each result, because touch latency differs by device.
- Show rolling medians (last 5) next to personal bests: one 10-second sample is noisy.
- Faster difficulty calibration over the first 3 plays per game, and rating decay after 14 days inactive.

## Phase 4: social and release (v1.0)
- Share card for the daily score (image, no account needed).
- Optional Play Games Services leaderboard for the daily challenge.
- Play Store listing: no health or cognitive-improvement claims (see FTC v. Lumos Labs, 2016).
- Baseline profile + R8 for fast cold start (target < 500 ms to interactive on mid-range devices).

## Risks
- Touch latency differs between devices (roughly 30 to 100 ms). Compare reaction scores only with your own past runs, never across devices.
- Brain-training efficacy claims are a legal risk. Market the app as a fun, quick game.
- A "one challenge per open" loop can feel intrusive. A quick app switch (< 30 s) resumes without a new challenge.
