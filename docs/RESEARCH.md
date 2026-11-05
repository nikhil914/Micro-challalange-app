# Micro-Challenge Brain Trainer: Research Report

Research date: 2026-11-05. Scope: an Android app ("MicroMind") that gives the user one 10-second micro-challenge (memory, reaction, small puzzle) every time it opens. Scores are stored on the device.

This report covers six areas:

1. The cognitive science behind tasks of 10 seconds or less, and the legal limits on claims.
2. A competitor analysis.
3. Engagement design for a "one challenge per open" loop.
4. The Android technology stack and the timing precision it allows.
5. A no-code path in MIT App Inventor.
6. A specification of 12 challenges.

Section 6 maps onto the existing `core` contract: score 0..1000, performance 0..1 with a target of about 0.7, difficulty 1..10 (see `docs/CHALLENGE_CONTRACT.md`).

> Sourcing note: these hosts could not be fetched directly from this environment: ftc.gov, humanbenchmark.com, ai2.appinventor.mit.edu and unstar.app. Facts about them come from search-engine extracts of those pages and from secondary coverage, and are labeled as secondary where it matters. Competitor prices change by storefront and month. Treat every price below as indicative and check it in the store.

---

## 1. Cognitive science

### 1.1 What a 10-second window can and cannot measure

A 10-second window holds one short trial, or a burst of 5 to 25 very short trials. That limit shapes three things:

- **Reliability.** Lab tasks use dozens to hundreds of trials. For example, the 3-minute brief Psychomotor Vigilance Test (PVT-B) was shortened from the standard 10 minutes. To keep its sensitivity, the authors cut the inter-trial interval to 2-5 s and lowered the "lapse" threshold from 500 ms to 355 ms. ([Basner et al. 2011, PMC3197786](https://pmc.ncbi.nlm.nih.gov/articles/PMC3197786)) A 3-minute smartphone PVT has been validated against a 10-minute laptop PVT for detecting sleep loss. Smartphone RTs ran *faster* than laptop RTs, which shows that device effects are real. ([Grant et al. 2017, Behav Res Methods](https://dx.doi.org/10.3758/s13428-016-0763-8)) A 10-second sample is therefore a *game score*, not a measurement. The app should show trends as rolling medians over many plays, and never present one play as an assessment.
- **State vs. trait.** An ecological momentary assessment (EMA) study ran Stroop and go/no-go tasks several times a day on smartphones. The stable trait share of variance was moderate to high. The state-specific share was small for Stroop (about 1-11%) and larger for go/no-go (about 16-37%). ([Hachenberger et al. 2025, Behav Res Methods, open access](https://pub.uni-bielefeld.de/record/2999865)) Implication: go/no-go scores move with momentary state such as fatigue and time of day. Store a timestamp with each result so the trend can be read in context.
- **App vs. lab equivalence.** A smartphone Stroop app showed good agreement with a computer Stroop for RT: ICC 0.81 on congruent trials and 0.76 on incongruent trials. It also showed a systematic bias of about 40-76 ms. ([EncephalApp Stroop validity study, Univ. Bologna](https://cris.unibo.it/handle/11585/1017166)) A JMIR mHealth study found repeated smartphone Stroop and Trail Making testing feasible, with moderate validity. ([JMIR mHealth 2017;5(5):e68](https://mhealth.jmir.org/2017/5/e68/PDF)) Conclusion: compare users only against their own baseline on the same device, never against population norms.

### 1.2 Construct and task mapping, all doable in 10 seconds or less

| Construct | Classic paradigm | 10-s adaptation | Notes and evidence |
|---|---|---|---|
| Processing speed / simple RT | Simple visual RT, PVT | 3-5 trials with a random foreperiod of 1-3 s | Responses under 100 ms are false starts by PVT convention. A lapse is over 355 ms in PVT-B ([PMC3197786](https://pmc.ncbi.nlm.nih.gov/articles/PMC3197786)). Typical online simple-RT medians are about 250-275 ms, inflated by display and input lag. The often-quoted Human Benchmark median of about 273 ms appears only in third-party summaries, not in primary sources ([example summary](https://miniwebtool.com/reaction-time-test/)). |
| Working memory: verbal / numeric span | Digit span | Show N digits for about 1 s per digit plus a short gap, then recall | Capacity is about 3-5 chunks when chunking is blocked ([Cowan 2001, BBS](https://www.cambridge.org/core/journals/behavioral-and-brain-sciences/article/magical-number-4-in-shortterm-memory-a-reconsideration-of-mental-storage-capacity/44023F1147D4A1D44BDC0AD226838496); [PDF](https://memory.psych.missouri.edu/assets/doc/articles/2001/cowan-bbs-2001.pdf)). Digit strings allow chunking, so expect spans of 5-9. |
| Visuospatial WM | Corsi blocks, visual array / change detection | Light up a sequence of tiles, then repeat it. Or flash a grid pattern, then reproduce it. | Visual-array capacity levels off at 3-4 objects ([Saults & Cowan, PMC2864034](https://pmc.ncbi.nlm.nih.gov/articles/PMC2864034)). Sequence length is the difficulty knob. |
| Inhibition / interference | Stroop | Word-ink conflict, 6-12 trials | Color Stroop depends on color perception, so it needs an accessible variant (section 4.7). Numerical Stroop (physical size vs. value) or spatial Stroop (arrow direction vs. position) avoids the color dependence. |
| Response inhibition | Go/no-go, stop-signal | Stream of stimuli at about 600-900 ms each, roughly 75% "go" | Error rates on go/no-go and Stroop correlate, which supports construct validity ([Hachenberger 2025](https://pub.uni-bielefeld.de/record/2999865)). Commission errors are the key metric. |
| Selective attention / visual search | Feature / conjunction search, flanker | "Odd one out" grid, flanker arrows | Conjunction targets and grid size are the knobs. |
| Numerical cognition / mental math | Arithmetic verification, number comparison | Speeded sums, true/false equations, "bigger number" | The distance effect makes close magnitudes slower, which gives a natural difficulty knob. |
| Task switching | Color-shape switch | Rule flips every K trials | Usable in 10 s with short trials. Instructions must be very clear. |

### 1.3 Evidence on brain-training efficacy

- **Near transfer is real. Far transfer is mostly absent.** In a six-week BBC online study, 11,430 adults completed the training. They improved on the trained tasks but showed "no evidence for any generalized improvements in cognitive function". ([Owen et al. 2010, Nature 465:775, PMC2884087](https://pmc.ncbi.nlm.nih.gov/articles/PMC2884087))
- **Simons et al. 2016**, *Psychological Science in the Public Interest* 17(3):103-186, reviewed every peer-reviewed study cited by leading brain-training companies. They found extensive evidence of improvement on trained tasks and less evidence for closely related tasks. They found little evidence of improvement on distantly related tasks or everyday cognitive performance, and most studies had design flaws. ([APS summary](https://www.psychologicalscience.org/publications/brain-training.html); [Illinois news](https://las.illinois.edu/news/2016-10-03/researchers-little-evidence-brain-training-games-yield-real-world-benefits))
- **Working-memory training meta-analysis** (Melby-Lervåg, Redick & Hulme 2016, 87 publications, 145 comparisons): there are near-transfer WM gains. There is no convincing far transfer to intelligence, reading or arithmetic against active controls. ([PMC4968033](https://pmc.ncbi.nlm.nih.gov/articles/PMC4968033))
- **The ACTIVE trial** (2,802 healthy older adults) is the strongest positive signal. At 10 years, the speed-of-processing training arm showed a 29% lower hazard of dementia (HR about 0.71), with a dose-response relationship. The memory and reasoning arms showed no such effect. ([Edwards et al. 2017, PMC5700828](https://pmc.ncbi.nlm.nih.gov/articles/PMC5700828)) Caveats: it was a secondary analysis, dementia was partly self- or proxy-reported, the training was a specific supervised protocol (Useful Field of View), and the dose was about 10 sessions plus boosters. It does **not** license claims for 10-second casual games.
- **Scientific dispute.** In 2014 the Stanford Center on Longevity and the Max Planck Institute issued a statement signed by about 70 scientists: brain games do not offer a scientifically grounded way to reduce or reverse decline. A rebuttal from about 130 scientists agreed that claims are "frequently exaggerated" but argued that some evidence exists. ([Umeå summary](https://www.umu.se/en/news/a-consensus-on-the-brain-training-industry-from-the-scientific-community_5843556); [Chronicle](https://www.chronicle.com/blogs/wiredcampus/do-brain-training-games-work-it-depends-on-which-scientists-you-ask))
- **Medical-device precedent.** NeuroNation MED, a CE-marked medical device, is listed in Germany's DiGA directory for mild cognitive impairment. It is prescribed at a €499 tariff after a 288-person trial. ([neuronation-med.de HCP page](https://neuronation-med.de/hcp_en); [Pharmazeutische Zeitung](https://www.pharmazeutische-zeitung.de/gehirntraining-auf-rezept-157874/)) This is the route a health claim actually requires: regulatory clearance plus an RCT. A casual app must stay out of it.

### 1.4 The FTC v. Lumos Labs precedent (January 2016)

- Lumos Labs paid $2M in redress. A $50M judgment was suspended because of the company's financial condition. Lumos had to notify subscribers and offer an easy way to cancel auto-renewal. The FTC alleged unsupported claims that Lumosity improves performance at work and school, delays age-related decline, and protects against MCI, dementia and Alzheimer's. It also alleged undisclosed incentivized testimonials (contest prizes). Future claims must rest on "competent and reliable scientific evidence". ([FTC press release](https://ftc.gov/news-events/press-releases/2016/01/lumosity-pay-2-million-settle-ftc-deceptive-advertising-charges); [TechCrunch](https://techcrunch.com/2016/01/06/lumosity-brain-training-app-maker-to-pay-2-million-settlement-to-ftc/amp/); [AFS Law](https://www.afslaw.com/perspectives/the-fine-print/lumos-labs-settles-ftc-over-brain-training-program-claims))

### 1.5 Claims the app must NOT make, with safe alternatives

| Do NOT say | Why | Safe wording |
|---|---|---|
| "Improves memory / IQ / focus", "makes you smarter" | Far transfer is unsupported (Owen 2010, Simons 2016, Melby-Lervåg 2016) | "Quick games that *challenge* memory, speed and focus" |
| "Prevents / delays dementia, Alzheimer's, cognitive decline", "keeps your brain young" | FTC v. Lumos; Play health policy | Do not mention these at all |
| "Clinically proven", "designed by neuroscientists" (unless true and substantiated) | Requires competent and reliable evidence | "Inspired by classic psychology tasks such as Stroop and digit span" |
| "Your brain age is 42", "cognitive score", "diagnose ADHD" | Implies an assessment the app cannot provide | "Your Reaction Tap personal best: 241 ms" |
| "Better at school / work / sports" | FTC-cited claim | none |
| Incentivized reviews without disclosure | FTC-cited practice | Never offer rewards for ratings |

Store listing: include the Google Play disclaimer that the app "is not a medical device and does not diagnose, treat, cure, or prevent any medical condition". ([Play Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291); [Health Content and Services policy](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en)) The existing code already notes "no medical claims" in `Skill`. Keep that rule in every copy string.

---

## 2. Competitor analysis

| App | Format | Session | Monetization (indicative) | Retention mechanics | Recurring complaints |
|---|---|---|---|---|---|
| **Lumosity** (Lumos Labs) | 40+ games (Speed Match, Memory Matrix, Train of Thought…), game-based daily "Workout" | Free: 3 games/day, replayable. Premium: 5-game workout. A few minutes. | Premium about $11.99/mo or $59.99/yr in US App Store listings ([Lumosity help](https://lumositybeta.zendesk.com/hc/en-us/articles/31155245841303-What-are-the-differences-between-a-Free-Account-and-a-Premium-Membership); [App Store](https://apps.apple.com/us/app/lumosity-mobile/id577232024?mt=8)) | Daily workout, streaks, LPI score and percentiles vs. age group, insights | Paywall after 3 games, repetitive games, the FTC history colors the brand ([nibble review](https://nibble-app.com/blog/lumosity-review)) |
| **Elevate** | 40+ games weighted to verbal, reading, writing and math | Personalized daily workout of 3-5 games | 7-day trial. About $39.99/yr reported; prices vary ([TapSmart](https://www.tapsmart.com/apps/review-elevate-award-winning-brain-training-app/?amp=1); [App Store](https://apps.apple.com/us/app/id875063456); [apppricinglab](https://apppricinglab.com/iap/apple/875063456)) | Streaks, skill-group "EPQ" proficiency, a calendar, polished feedback animations | Price; content plateaus after months; limited proof of long-term effect |
| **Peak** (Brainbow/Synaptic Labs) | 45+ games, "Brain Map" radar of skill areas, "Coach" | Daily workout of 3-5 games, about 10 min | Ads in free tier. Pro about $8.99/mo is the common figure ([nibble Peak pricing](https://nibble-app.com/blog/peak-pricing-cost); [nibble review](https://nibble-app.com/blog/peak-app-review); [App Store](https://apps.apple.com/ca/app/id806223188)) | Brain Map filling in, coach nudges, rotating free games | Free tier too limited; Pro users capped at the daily set; scores rise without real-life change |
| **NeuroNation** (Synaptikon, Berlin) | Exercise modules ("courses") per skill | About 5-10 min daily plan | Premium. Figures vary: about $8.99/mo, $59.99-$83.99/yr ([justuseapp reviews](https://justuseapp.com/en/app/821549680/neuronation-brain-training/reviews)) | Training plans, a medical-grade halo via NeuroNation MED (DiGA) | Subscription charges and renewals ("watch out for subscription charge") |
| **CogniFit** | Assessment-first ("cognitive test"), then training; also clinical/research products | Assessment plus game sessions | $9.99-$19.99/mo, $59.99-$119.99/yr depending on storefront and date ([App Store AU](https://apps.apple.com/au/app/cognifit-brain-test-training/id528285610); [nibble](https://nibble-app.com/blog/cognifit)) | Assessment-driven "brain profile" | Reported as the most-complained-about in its category: high annual price, clinical-sounding claims, cancellation routed to a web login (secondary, per the unstar.app 2026 comparison) |
| **Impulse** (GMRD Apps, `gen.tech.impulse.android`) | 30+ short games, Stroop and other neuropsych-inspired tasks | Short daily plan | 3-day trial, then subscription (one paywall shows about $28.99/yr) ([App Store](https://apps.apple.com/us/app/1451295827); [appgoblin](https://appgoblin.info/apps/gen.tech.impulse.android)) | Personalized plan, streaks, aggressive trial paywall | Heavy ads in free tier, ads reported even for paying users, thin content in some games ([Kimola review analysis](https://kimola.com/reports/impulse-brain-training-analysis-elevate-your-product-google-play-en-us-145129)) |
| **Human Benchmark** (web) | Single-purpose tests: Reaction Time, Sequence Memory, Number Memory, Verbal Memory, Visual Memory, Chimp Test, Aim Trainer, Typing | Each test 10 s to 2 min, no session concept | Free, ad-supported web; optional account to save scores | Global percentile histograms, personal bests, shareable results. No streaks, no nagging. | Desktop-centric. Reaction scores vary with hardware and display lag. ([test list, secondary](https://letsrandomize.pages.dev/guides/human-benchmark-tests/)) |

**Takeaways for MicroMind**

1. Every subscription incumbent sells a *5-10 minute workout*. Nobody owns the *10-second, zero-commitment* slot. Human Benchmark is closest in spirit (one test, instant score, personal best), but it is web-only with no habit loop.
2. The top complaint is the paywall, and in particular cancellation friction and surprise renewals. That is also the FTC's enforcement focus (section 3.6). A free, local-only, no-account app with honest copy is a real differentiator.
3. The most-loved mechanic is the *instant numeric score with a personal best* (Human Benchmark). Next comes *visible progress per skill* (Peak Brain Map, Elevate). Both work offline.
4. Avoid percentile-vs.-population displays. Device latency makes reaction percentiles unfair (section 4.2). Percentile displays also imply an assessment.

---

## 3. Engagement design for a "one challenge per open" loop

### 3.1 Core loop

```
Open app -> (cold start <= 1 s) -> 1-line instruction + "Tap to start" (or 3-2-1)
-> 10 s challenge -> score + delta vs. PB / last 7-day median -> [Again?] [Done]
```

- **Time to first challenge is the main metric.** Skip splash and onboarding on later opens. Put the instruction on the start card and show it for at least 1 s. A tap on the card starts the window. Never start the clock the instant the screen appears: the user may still be looking elsewhere.
- **"One per open" is the default, not a lock.** After the result, offer "Another?" without a limit. A hard limit would turn into a dark pattern if a paywall ever follows it. A soft cap is fine, as long as it says plainly when today's set is done ("Today's set: 3/3. Play more anytime.").
- **Challenge selection.** Weighted random over the types: weight the least-recently-played type higher, avoid the same type twice in a row, and spread skills evenly (`ChallengePicker`). Pick a deterministic **Daily Challenge** each day, seeded by a date hash, so the same date gives the same challenge and seed on every device. This makes sharing comparable without a server.

### 3.2 Streaks, the humane version

- Duolingo data, all secondary reporting: the 1-to-2-day transition and the first 7 days carry the most retention weight, and the curve flattens after day 7. Streak Freeze reportedly cut churn among at-risk users. A streak-wager feature raised D14 retention by about 14%. ([Duolingo blog](https://blog.duolingo.com/duolingo-streak-research/); [Lenny's podcast summary](https://www.recall.it/summary/lennys-podcast/behind-the-product-duolingo-streaks-or-jackson-shuttleworth-group-pm-retention-team))
- **Recommended rules** (`StreakTracker`):
  - One completed challenge on a local calendar day counts.
  - Compute the day in the user's current time zone from the wall clock. Use the monotonic clock only for in-game timing.
  - Grant one automatic **free "rest day" token per 7 streak days**, capped at 2. This protects against a single missed day without loss aversion.
  - Show "longest streak" alongside "current streak", so a reset does not erase all visible progress.
  - Never let a streak be bought, and never send guilt-driven notifications ("Your brain misses you").

### 3.3 Adaptive difficulty

- **Staircase.** Levitt's transformed up-down methods converge on fixed performance levels. 1-up/2-down targets 70.7% correct, 1-up/3-down targets 79.4%, 1-up/4-down targets 84.1%. ([Levitt 1971, JASA 49:467](https://scholarhub.vn/publication/Transformed-Up-Down-Methods-in-Psychoacoustics/39c302c9-c786-46fc-8b50-ea0b0ecb1908); [Purdue notes](https://engineering.purdue.edu/~ece511/LectureNotes/pp12.pdf)) The existing `DifficultyRating` uses a continuous variant: `rating += (perf - 0.7) * 2.4`, clamped to 1..10. That is a proportional controller aimed at 0.7, which fits a continuous performance value better than binary up/down.
- **Elo-style ratings** (Math Garden). The learner rating and the item difficulty are updated together. The scoring rule rewards fast correct answers and punishes fast errors, which discourages guessing. ([Klinkenberg et al. 2011, summarized in Frontiers 2019](https://www.frontiersin.org/articles/10.3389/fpsyg.2019.00620); [PMC6450197](https://pmc.ncbi.nlm.nih.gov/articles/PMC6450197)) Math Garden's "high speed, high stakes" rule gives a correct answer `+(d - t)` and an error `-(d - t)`, where *d* is the time limit and *t* the RT. That rule transfers directly to MATH_SPRINT and QUICK_COMPARE.
- **Recommendations**
  - Keep one rating per `ChallengeType`, not one global rating.
  - Use an asymmetric step: up by `0.6*(perf-0.7)` and down by `1.0*(perf-0.7)`. Failing hard twice in a row is the fastest way to churn, so drop faster than you climb.
  - Add an uncertainty term (Glicko-like). Use larger steps for the first 5 plays of a type, then decay to the base step. This speeds calibration.
  - **Decay toward the start value after inactivity.** After 14 or more days away, apply `rating -= 0.5`, so the first play back is winnable.
  - Show a level badge ("Lv 6") and do not hide that difficulty adapts. Transparency is better than a rubber-band feel.

### 3.4 Feedback, personal bests and haptics

- Result card: big score (0-1000), then a one-line concrete detail ("Avg 287 ms", "7 digits"), then a delta against the *personal best* and the *7-day median*. Celebrate a PB with a short animation and a CONFIRM haptic.
- **Haptics** ([Haptics principles](https://developer.android.com/develop/ui/views/haptics/haptics-principles); [Haptic feedback guide](https://developer.android.com/develop/ui/views/haptics/haptic-feedback)):
  - Use `HapticFeedbackConstants` through `View.performHapticFeedback`. This needs no VIBRATE permission and respects the system haptics setting. In Compose, use `LocalHapticFeedback` or `LocalView.current.performHapticFeedback(...)`.
  - Use CONFIRM (short and light) for a correct answer and REJECT (stronger) for an error or false start.
  - Keep haptics sparse: "less is more", so never vibrate on every tick.
  - **Never** use haptics as the stimulus for reaction tasks. Actuator latency varies by device.
- **Sound** is off by default. If enabled, keep it short, and never use it as an RT stimulus. Android audio output latency varies widely between devices.

### 3.5 Home-screen surfaces

- **Glance widget.** Show today's streak, the daily challenge name, and a "Play" button that deep-links straight into the challenge (section 4.5).
- **App shortcuts:** "Daily challenge", "Reaction test", "Random". Launchers usually show at most about 4 static plus dynamic shortcuts. ([App shortcuts overview](https://developer.android.com/develop/ui/views/launch/shortcuts))
- **One optional daily reminder** at a time the user picks, off by default, using a notification channel. It must ask for the `POST_NOTIFICATIONS` runtime permission (API 33+).

### 3.6 Dark patterns to avoid

The FTC's 2022 staff report *Bringing Dark Patterns to Light* names hard-to-cancel subscriptions, disguised ads and forced continuity. Cancellation should be at least as easy as sign-up. ([Orrick summary](https://infobytes.orrick.com/2022-09-23/ftc-reports-use-dark-patterns/); [Mayer Brown on subscription enforcement](https://www.mayerbrown.com/en/perspectives-events/publications/2022/11/subscription-lessons-from-us-ftcs-latest-dark-patterns-settlement))

For MicroMind, avoid these patterns:
- Confirmshaming ("No thanks, I don't care about my brain").
- Fake urgency or countdown offers.
- Streak loss used as paywall leverage.
- Interstitial ads between open and challenge.
- Pre-checked notification opt-ins.
- Rating prompts gated on a reward.
- Fake "brain age" assessments as a funnel.

Use Play's in-app review API only after a PB, at most once per quarter.

---

## 4. Android technology

### 4.1 Stack, current as of late 2026

- **Kotlin 2.x with the K2 compiler** and the Compose compiler Gradle plugin (`org.jetbrains.kotlin.plugin.compose`).
- **Compose BOM 2026.09.00** is the newest listed. Material3 1.4.0 and Foundation 1.12.x are the newest in the mapping table. ([BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping))
- Single-Activity, Navigation Compose, a ViewModel with `StateFlow`, and `collectAsStateWithLifecycle`.
- Edge-to-edge is enforced when targeting API 35+. Handle `WindowInsets`.
- Keep game logic in a pure-Kotlin JVM module (`:core`) so unit tests run without an emulator. The repo already does this.
- **Target SDK.** From **31 Aug 2026**, new apps and updates must target **API 36 (Android 16)**. An extension to 1 Nov 2026 was available. Existing apps must target 35 or higher to stay visible to new users on newer OS versions. ([Target SDK requirements](https://developer.android.com/google/play/requirements/target-sdk)) Expect API 37 by Aug 2027.
- **Android 16, API 36, large screens.** On displays of sw600dp or wider, the system ignores `screenOrientation`, `resizeableActivity=false` and aspect-ratio limits. An opt-out exists at API 36 and goes away at API 37. Game layouts must therefore handle landscape and tablets: use max-width containers and `WindowSizeClass`. ([Android Developers Blog, Jan 2025](https://android-developers.googleblog.com/2025/01/orientation-and-resizability-changes-in-android-16.html?m=1))

### 4.2 Timing precision for reaction tests

**Clocks** ([SystemClock reference](https://developer.android.com/reference/android/os/SystemClock)):

| Clock | Monotonic | Counts deep sleep | Use |
|---|---|---|---|
| `System.currentTimeMillis()` | No: user and network can change it | n/a | Streak calendar day, result timestamps only |
| `SystemClock.uptimeMillis()` / `uptimeNanos()` (API 35) / `System.nanoTime()` | Yes | No | **Same time base as input events.** Handler and Choreographer use it. |
| `SystemClock.elapsedRealtime()` / `elapsedRealtimeNanos()` | Yes | Yes | General interval timing that spans sleep, such as the "10-minute cooldown" |

**Key facts**
- `MotionEvent.getEventTime()` is on the `uptimeMillis` base. `getEventTimeNanos()` is public from API 34. It has ns *precision* but not ns *accuracy*. ([API diff](https://developer.android.com/sdk/api_diff/u-beta2-incr/changes/android.view.MotionEvent))
- Compose `PointerInputChange.uptimeMillis` is the event's timestamp, carried over from the MotionEvent. ([PointerInputChange](https://developer.android.com/reference/kotlin/androidx/compose/ui/input/pointer/PointerInputChange)) **Use the event timestamp, not "now" inside the handler.** Calling `nanoTime()` in the callback adds dispatch and thread-contention delay. ([SO discussion](https://kiwix.lema.org/stackoverflow.com_en_all_2023-11/questions/9764310/measure-elapsed-time-between-two-motionevents-in-android))
- The stimulus onset should be the **frame time** of the first frame that draws the stimulus, not the moment the state flag flipped. In Compose, read the `frameTimeNanos` that `withFrameNanos {}` passes in, recorded when the stimulus becomes visible. `Choreographer.FrameCallback.doFrame(frameTimeNanos)` gives the same thing. Both are on the `System.nanoTime` (uptime) base, so `rtMs = (event.uptimeMillis*1e6 - stimulusFrameNanos) / 1e6`.
- **Pipeline delays.** The actual photons appear about 1-2 vsync periods after the frame time, because of render-ahead and SurfaceFlinger composition: 8-33 ms at 60-120 Hz. Touch input is batched into the next frame's INPUT phase. AOSP resampling uses a 5 ms resample latency and up to 8 ms of prediction. ([Blur Busters on Android vsync pipeline](https://forums.blurbusters.com/viewtopic.php?p=51179); [AOSP InputTransport](https://gerrit.omnirom.org/plugins/gitiles/android_frameworks_native/+/f1e9f02a8cab0b08b2d425fec01a1f12d4b48670/libs/input/InputTransport.cpp))
- **End-to-end touch latency is device-dependent and large.** It is often 80 ms or more for tracking, and it has barely improved in a decade. ([TriboTouch paper](https://karan-ahuja.com/assets/docs/paper/tribotouch.pdf)) Older tap-to-display measurements ran from 75 to 168 ms across devices ([Agawi TouchMarks via RCR Wireless](https://www.rcrwireless.com/?p=32671)). Google's WALT tool showed drag latencies of about 10-36 ms on the *input* side alone for Nexus devices. ([Android Developers Blog: WALT](https://android-developers.googleblog.com/2016/04/a-new-method-to-measure-touch-and-audio.html)) Differences between devices can be several-fold ([Aalto thesis](https://core.ac.uk/download/145239672.pdf)).

**Recommendations**
1. Use `detectTapGestures(onPress = …)` or `awaitFirstDown()`, so the response registers on **touch down**, not on release. The contract already says this.
2. Compute RT as `downEvent.uptimeMillis - stimulusFrameUptimeMs`. Then *do not* subtract a guessed device latency. Instead, always compare the user against their own history on this device. Store `Build.MODEL` and the refresh rate with each result, and reset or flag PBs when the device changes.
3. Treat an RT under 100 ms as a false start. Score 0 for that trial and show "Too soon!".
4. Randomize foreperiods, for example 800-2,500 ms uniform or exponential. Fixed foreperiods let users anticipate.
5. Request the highest refresh rate during RT games with `Window.attributes.preferredDisplayModeId`, or the frame-rate API, and keep the screen on. Avoid heavy recomposition near the stimulus: draw it in a `Canvas` driven by a single state read, and precompose everything.
6. In the core engine, time only through an injected `Clock` (`monotonicMs`). The host pauses the clock when the app goes to the background (`PausableClock`), and the session voids the trial if `onStop` fires mid-challenge.

### 4.3 Local storage: DataStore vs SharedPreferences vs Room (and TinyDB)

- **TinyDB** in MIT App Inventor is a persistent per-app key-value store (`StoreValue(tag, value)`, `GetValue(tag, valueIfTagNotThere)`, `Namespace`). Under the hood it is backed by Android `SharedPreferences`. The direct native equivalent is **Preferences DataStore**.
- **DataStore 1.3.0** is the current version:
  - It is coroutine and Flow based.
  - Its writes are transactional (`updateData` is an atomic read-modify-write).
  - It is safe on the main thread, unlike the synchronous `commit()` of SharedPreferences.
  - Multi-process support has existed since 1.1.0.
  - It comes in Preferences (key-value), typed JSON and Proto variants.
  - Google recommends migrating off SharedPreferences.
  - Two rules: never create two DataStore instances for the same file, and keep the stored type immutable.
  - Files live in `files/datastore/` and are included in Auto Backup. Use `data_extraction_rules.xml` to control backup.
  - For large datasets, partial updates or referential integrity, use **Room** instead.

  ([DataStore guide](https://developer.android.com/topic/libraries/architecture/datastore))
- **Recommended split**
  - **Preferences DataStore:** settings such as reminder time, sound, haptics, reduced-motion override and color-safe mode.
  - **Typed JSON DataStore** (kotlinx-serialization): a `Progress` object holding per-type rating, PB, play count, streak state and the last 30 results per type in a ring buffer. This fits easily under 50 KB.
  - **Room:** only if the app keeps the full history for charts, with an unbounded row count, or needs queries such as "median by hour of day". A history table `results(id, type, score, perf, difficulty, durationMs, detail, wallMs, deviceModel)` indexed on `(type, wallMs)` is the natural shape.
  - Keep the `KeyValueStore` interface already in `:core`. Implement it with DataStore in `:app` and with an in-memory map in tests, so the App Inventor model (TinyDB) and the native model share one abstraction.

### 4.4 Lifecycle and integrity details

- Pause on `ON_STOP` and void the run if the app is backgrounded for more than 500 ms during a challenge. The tasks are timed, so a pause would help the player.
- Restore a cold start in the middle of a challenge as "abandoned". Do not resume it.
- Seed the RNG per run and store the seed with the result for reproducibility. Daily challenge seed: `LocalDate.toEpochDay()`.

### 4.5 Glance home-screen widget

- Glance builds app widgets with Compose-style Kotlin APIs on the Compose *runtime*. It is not interoperable with regular Compose UI components, so do not mix the two. ([Glance overview](https://developer.android.com/develop/ui/compose/glance); [Canonical widget layouts](https://developer.android.com/design/ui/mobile/guides/widgets/layouts); [Widget quality](https://developer.android.com/docs/quality-guidelines/widget-quality))
- Design:
  - Use a `GlanceAppWidget` with `SizeMode.Responsive`: 2x1 shows the flame and streak count, 2x2 adds the daily challenge name and a Play button.
  - Read DataStore inside `provideGlance`.
  - Call `MicroWidget().updateAll(context)` after each result is saved.
  - The `actionStartActivity` deep link (`micromind://play?type=DAILY`) opens the challenge directly.
  - No periodic update is needed: the widget changes only when the user plays. Use a midnight `WorkManager` job only to roll the "played today" indicator.

### 4.6 App shortcuts

Use static shortcuts in `res/xml/shortcuts.xml` (Daily, Reaction, Random) and one dynamic "Replay last: Color Clash" through `ShortcutManagerCompat.pushDynamicShortcut`. Only the `MAIN`/`LAUNCHER` activity can own shortcuts, and most launchers show at most 4. ([Shortcuts overview](https://developer.android.com/develop/ui/views/launch/shortcuts))

### 4.7 Accessibility

- **Color-blind safety:**
  - Never encode meaning with color alone (WCAG 1.4.1, [Understanding Use of Color](https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html)).
  - Go/no-go: green **circle** vs. red **square with an X**.
  - Reaction: the background changes *and* a large "TAP!" glyph appears.
  - Use a CVD-safe palette such as Okabe-Ito ([J*Fly color universal design](https://jfly.uni-koeln.de/color/)), and check contrast of 3:1 or more for graphics.
  - The **Color Clash (Stroop)** game depends on color by its nature. Offer a "Shape Clash" variant: the word "CIRCLE" drawn inside a square, answered by shape. Or always pair each ink with a pattern or letter badge. The existing `GameColors` has `.label` for this.
- **TalkBack.**
  - Give every tile a `contentDescription` or `semantics { }`.
  - Announce the score with a `liveRegion`.
  - RT games cannot be fairly timed with screen-reader explore-by-touch, which adds a double-tap. Mark them "visual-only" and exclude them from the picker when `AccessibilityManager.isTouchExplorationEnabled`. Offer the untimed-input games (number memory, math) instead.
  - Use touch targets of 48dp or more, and 64dp or more in games.
  - Follow the [Compose accessibility guide](https://developer.android.com/develop/ui/compose/accessibility).
- **Reduced motion.**
  - Read `Settings.Global.ANIMATOR_DURATION_SCALE`. If it is 0 ("Remove animations"), disable decorative animation.
  - Provide an in-app override.
  - Compose exposes `MotionDurationScale` for this ([API](https://composables.com/docs/androidx.compose.ui/ui/interfaces/MotionDurationScale)). Reports conflict on whether Compose honors the system scale automatically ([Kotlin Slack thread](https://slack-chats.kotlinlang.org/t/16196587/i-m-seeing-conflicting-information-regarding-whether-develop?t=v2)), so handle it explicitly.
  - Stimulus changes in games are functional, not decorative, so keep them. Make them instant cuts, with no flashing above 3 Hz (WCAG 2.3.1).
- Respect font scaling up to 200% on instruction and result screens. Use non-scaling digits only inside fixed game grids, and test them.

### 4.8 Play policy checklist

- **Health apps declaration:** required for all apps, completed under App content. Declare the app as a casual game or trainer with no health features. ([Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291))
- **No misleading health claims** in the listing, screenshots or in-app text (section 1.5), and include the "not a medical device" disclaimer. ([Health Content & Services](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en); [Deceptive Behavior policy](https://support.google.com/googleplay/android-developer/answer/9888077))
- **Data safety form:** with local-only storage and no SDKs, declare "No data collected". Re-check this if analytics or crash reporting is added.
- **Target API 36** (section 4.1). A privacy policy URL is still required in Play Console, even for no-data apps.
- If the app is listed for children or under Families, the Families policy applies. Simplest is to target 13+.
- Subscriptions, if ever added, must follow Play's subscription policy: clear terms, and one-tap cancellation through Play.

---

## 5. Alternative path: MIT App Inventor (no-code)

App Inventor fits the concept well. Its components map one-to-one onto the native design: **Clock** for timing, **TinyDB** for persistence and **Canvas** for drawing. (Reference pages under `ai2.appinventor.mit.edu/reference/components/` could not be fetched here. The behavior below comes from the component docs as summarized by the [App Inventor community](https://community.appinventor.mit.edu/t/how-do-you-store-a-timer-value-in-tiny-databases/69202) and [course tutorials](https://sites.google.com/site/middleschoolandroid/home/app-inventor-tutorials/day-7-to-do-list--tinydb).)

**Component mapping**

| Native concept | App Inventor |
|---|---|
| Monotonic clock | `Clock.SystemTime` (ms number). Store ms numbers, not Clock "instants": instants cannot be saved in TinyDB ([community thread](https://community.appinventor.mit.edu/t/screen-resets-game-attempts-when-returning-need-to-preserve-time-using-tinydb/149673)). |
| Frame / tick loop | `Clock.TimerInterval` = 50-100 ms, `TimerEnabled`, and a `Clock.Timer` event. It ticks only while the screen is active unless `TimerAlwaysFires`. |
| DataStore | `TinyDB` (`Namespace` = "micromind"); tags such as `pb_reaction`, `rating_math`, `streak`, `lastPlayDay` |
| Canvas / Compose | `Canvas` + `ImageSprite`/`Ball` for tiles. `Canvas.TouchDown` registers on touch down. |
| Haptics | `Sound.Vibrate(ms)` (coarse) |

**Block outline: Screen1 as the one-challenge host**

```
when Screen1.Initialize:
  global today <- Clock.FormatDate(Clock.Now, "yyyy-MM-dd")
  global type  <- pick random item from list [reaction, digits, math, gonogo]
     (avoid TinyDB.GetValue("lastType"))
  global level <- TinyDB.GetValue("rating_" & type, 2)
  show StartCard ("Tap to start" + instruction)

when StartCard.Click:
  global startMs <- Clock.SystemTime
  ChallengeClock.TimerInterval <- 50 ; ChallengeClock.TimerEnabled <- true
  call setup_<type>(level)

when ChallengeClock.Timer:
  elapsed <- Clock.SystemTime - startMs
  TimerBar.Width <- percent( (10000 - elapsed)/100 )
  if elapsed >= 10000: call finish

# Reaction example
procedure setup_reaction: global goAt <- startMs + random integer 1000..3000 ; Canvas.BackgroundColor <- gray
(in Timer) if not shown and Clock.SystemTime >= goAt: Canvas.BackgroundColor <- green; Label "TAP!"; global shownMs <- Clock.SystemTime ; shown <- true
when Canvas.TouchDown(x,y):
  if not shown: score <- 0 ; label "Too soon!" ; call finish
  else: rt <- Clock.SystemTime - shownMs ; call finish

procedure finish:
  ChallengeClock.TimerEnabled <- false
  compute score (same formulas as section 6) ; perf
  TinyDB.StoreValue("pb_" & type, max(score, TinyDB.GetValue("pb_" & type, 0)))
  TinyDB.StoreValue("rating_" & type, clamp(level + (perf - 0.7)*2.4, 1, 10))
  streak logic: if lastPlayDay = yesterday -> streak+1 ; if = today -> same ; else -> 1
  TinyDB.StoreValue("lastPlayDay", today) ; show ResultCard
```

**Limits to tell no-code users about:**
- Timer ticks jitter by tens of ms.
- `Clock.SystemTime` is wall-clock based.
- Touch events are delivered through the App Inventor runtime, which adds overhead.

So App Inventor reaction times read noticeably higher and noisier than the native app's. They are fine for personal-best comparison on one device, but not comparable to the native app. Store history as a CSV-encoded list under one TinyDB tag, and cap it at about 50 entries.

---

## 6. Concrete recommendations: 12 micro-challenge designs

Common rules, matching `CHALLENGE_CONTRACT.md`:

- The window is 10,000 ms from the start tap.
- `score = clamp(raw × difficultyBonus(d))`, where `difficultyBonus(d) = 1 + 0.05(d-1)` and the result is clamped to 0..1000.
- `performance` is in 0..1. A value of 0.7 means "good, appropriately hard" and feeds the staircase.
- `D` = difficulty 1..10.
- The run ends early when the task is fully answered or a fatal error occurs.
- `finish()` with no input returns score 0 and performance 0.

### 6.1 Reaction Tap (SPEED): implemented as `REACTION_TAP`

- **Rules:**
  - 3 trials.
  - Each trial waits a random foreperiod, uniform in `[1.0, 2.6 − 0.08D]` s, plus a 400 ms minimum.
  - Then a full-screen "TAP!" plus a background change. Tap on touch down.
  - A tap before the stimulus, or within 100 ms of it, is a false start: the trial scores 0 and shows "Too soon".
  - A trial with no tap within 1,000 ms after the stimulus is a miss, with RT counted as 1,000.
- **Timing:** about 3 × (1.8 + 0.3) ≈ 6.5 s, which fits 10 s.
- **Score:** take the median RT `m` of valid trials. `raw = 1000 × clamp((600 − m)/400, 0, 1)`, so 200 ms or faster gives 1000 and 600 ms or slower gives 0. Multiply by `(valid/3)`.
- **Performance:** `clamp((500 − m)/250, 0, 1) × valid/3`.
- **Difficulty knob:** higher `D` gives shorter, less predictable foreperiods and adds catch trials (no stimulus, from D 6) that must be withheld. Raw RT is not "adaptive": the knob changes vigilance demand.

### 6.2 Go / No-Go (SPEED / inhibition): `GO_NO_GO`

- **Rules:**
  - A stream of stimuli in the screen center.
  - GO is a green circle. NO-GO is a red square with an X.
  - Stimulus duration `SD = 900 − 50D` ms (850 down to 400), with a 150 ms blank between stimuli.
  - The no-go ratio is 25%.
  - A tap on GO before it disappears is a hit. A tap on NO-GO is a commission error.
- **Timing:** the stream runs the full 10 s, giving 11 to 18 stimuli.
- **Score:** `raw = 1000 × (hits/goCount) × (1 − commissions/noGoCount)^1.5 × speedFactor`, where `speedFactor = clamp((SD − meanHitRT)/(SD − 200), 0.4, 1)`.
- **Performance:** `0.5×hitRate + 0.5×(1 − commissionRate)`.
- **Difficulty knob:** shorter `SD`. From D 7, add a lure: a green square counts as NO-GO, which requires a conjunction judgment.

### 6.3 Color Clash, Stroop (ATTENTION / interference): `COLOR_WORD`

- **Rules:**
  - A color word (RED, BLUE…) is drawn in an ink color. Tap the button matching the **ink**.
  - There are 4 buttons, each showing color plus label, so the game stays playable with CVD.
  - Incongruent proportion = `30% + 6%·D`, capped at 85%.
  - The next item appears immediately after each answer.
  - An error costs a 500 ms pause.
- **Timing:** answer as many as possible in 10 s, typically 8-14.
- **Score:** `raw = 70 × correct − 50 × errors`, clamped. About 14 correct with no errors gives about 1000 at D 1 after the bonus.
- **Performance:** `accuracy × clamp(correct/10, 0, 1)`.
- **Difficulty knob:**
  - A higher incongruent ratio.
  - From D 5, 6 colors and 6 buttons.
  - From D 8, button positions shuffle every item.
- **Accessibility variant:** "Shape Clash", with the shape word drawn inside a different shape. It uses the same scoring.

### 6.4 Number Memory, digit span (MEMORY): `NUMBER_MEMORY`

- **Rules:**
  - Show `N = 3 + ⌊D × 0.6⌋` digits at once. That is 3 at D 1 and 9 at D 10.
  - Display time = `400·N` ms, capped at 3.6 s, so that display plus answer fits the window.
  - Then hide the digits and show a numeric keypad. The user types the digits and taps ✓.
- **Timing:** display of 3.6 s or less, then an answer window for the remaining time.
- **Score:** `raw = 1000 × (longest correct prefix / N)` if the answer is not fully correct. 1000 if fully correct, with no time bonus. Accuracy matters in memory tasks, not speed.
- **Performance:** 1.0 if fully correct, otherwise `prefix/N × 0.6`.
- **Difficulty knob:** `N`. The staircase lands near the user's span, where about 70% of attempts are exact.

### 6.5 Pattern Recall, Corsi-style (MEMORY, visuospatial): `SEQUENCE_MEMORY`

- **Rules:**
  - A 3×3 grid, 4×4 from D 7.
  - `L = 3 + ⌊D/2⌋` tiles light one at a time: 3 to 8 tiles, 450 ms on and 150 ms off each.
  - The user taps them back in order.
  - The first wrong tap ends the run.
- **Timing:** at L 8, presentation takes 4.8 s, leaving about 5 s to respond.
- **Score:** `raw = 1000 × correctTaps/L`.
- **Performance:** 1 if the whole sequence is correct, otherwise `0.6 × correctTaps/L`.
- **Difficulty knob:** sequence length and grid size. At D 9-10, tile spacing may also be reduced.

### 6.6 Math Sprint (MATH): `MATH_SPRINT`

- **Rules:**
  - Problems appear one at a time, answered on a numeric keypad. Auto-submit when the digit count matches the answer length.
  - D 1-3: single-digit addition.
  - D 4-6: two-digit plus one-digit addition, and subtraction.
  - D 7-8: 2d ± 2d and the multiplication table to 9×9.
  - D 9-10: mixed operations, for example 7×8−19.
- **Timing:** solve as many as possible in 10 s.
- **Score:** Math-Garden-style signed speed. For each item, `±(T − t)`, where `T` = 4 s per-item cap, `t` = solve time in s, + for correct and − for wrong. `raw = 45 × Σ`, then clamp. Example: 8 correct items at 1.2 s each give Σ = 22.4, so raw ≈ 1000.
- **Performance:** `accuracy × clamp(correct/5, 0, 1)`.
- **Difficulty knob:** operand size and the operation set.

### 6.7 Bigger Number, magnitude comparison (LOGIC / numerical): `QUICK_COMPARE`

- **Rules:**
  - Two values side by side. Tap the larger one.
  - Value types by level: D 1-3 integers; D 4-6 the numerical Stroop, where the font size conflicts with the value; D 7-8 simple fractions or decimals (3/4 vs 0.7); D 9-10 expressions (6×7 vs 45).
  - The numeric distance shrinks with `D`.
- **Timing:** 10 s burst.
- **Score:** `raw = 60 × correct − 60 × errors`, plus `+10 × streak` for consecutive correct answers.
- **Performance:** `accuracy × clamp(correct/12, 0, 1)`.
- **Difficulty knob:** the distance ratio (min/max approaching 1), a size-value conflict rate, and the representation type.

### 6.8 Odd One Out, visual search (ATTENTION): `ODD_ONE_OUT`

- **Rules:**
  - A grid with one item that differs from the rest. Tap it.
  - On a hit, a new grid appears.
  - D 1-3: a color or shape feature difference.
  - D 4-7: a rotation difference (◐ vs ◑).
  - D 8-10: a conjunction difference, where the target shares one feature with each distractor set.
  - Grid size is `3×3` up to `6×6`.
- **Timing:** 10 s burst, typically 4-10 grids.
- **Score:** `raw = 140 × found − 70 × wrongTaps`.
- **Performance:** `accuracy × clamp(found/6, 0, 1)`.
- **Difficulty knob:** grid size, feature subtlety and conjunction search.

### 6.9 Flanker Arrows (ATTENTION / inhibition): new, `FLANKER`

- **Rules:**
  - A row of 5 arrows, such as `<< > <<`. Swipe or tap the side the **center** arrow points to.
  - Incongruent ratio = `30 + 5D` %.
  - From D 6, the row's position jitters vertically.
  - From D 8, there are 7 arrows and a 200 ms pre-cue.
- **Timing:** 10 s burst, 10-18 trials.
- **Score:** `raw = 60 × correct − 60 × errors`, multiplied by `clamp((900 − meanRT)/500, 0.5, 1)`.
- **Performance:** accuracy weighted by count, as in Stroop.
- **Difficulty knob:** incongruence ratio, flanker count and jitter.
- Why add it: it is a color-free inhibition task, an accessible complement to Stroop.

### 6.10 Chimp Grid, spatial ordering memory (MEMORY): new, `CHIMP_ORDER`

- **Rules:**
  - Numbers 1..K appear at random cells of a 5×4 grid.
  - When the user taps "1", all the other numbers become blank tiles.
  - The user then taps them in ascending order.
  - K = `3 + ⌊D × 0.7⌋`, so 3 to 10.
  - Optional exposure limit from D 6: auto-mask after `300·K` ms.
- **Timing:** 10 s total.
- **Score:** `raw = 1000 × correctInOrder/K`.
- **Performance:** 1 if complete, otherwise `0.6 × fraction`.
- **Difficulty knob:** K, the exposure limit and grid density. This is a Human-Benchmark-style favorite.

### 6.11 Task Switch, color or shape rule (ATTENTION / flexibility): new, `RULE_SWITCH`

- **Rules:**
  - Each item is a shape (circle or square) in a color (orange or blue). Both colors are CVD-safe, and the shapes are distinct.
  - The cue banner reads "SHAPE" or "COLOR". The two answer buttons change their labels to match the cue.
  - The rule switches on average every `max(1, 5 − ⌊D/2⌋)` items, unpredictably from D 6.
- **Timing:** 10 s burst.
- **Score:** `raw = 70 × correct − 70 × errors`. An extra `+20` is awarded for each correct answer on a switch trial.
- **Performance:** accuracy weighted by count.
- **Difficulty knob:** switch frequency, cue-to-target interval (500 down to 0 ms) and cue predictability.

### 6.12 Mini Logic Grid, tiny puzzle (LOGIC): new, `LIGHTS_OUT` / `SLIDE_SUM`

- **Rules, Lights-Out mini:**
  - A 3×3 board, solvable in `k = 1 + ⌊D/3⌋` moves, which gives 1 to 4.
  - Tapping a cell toggles it and its orthogonal neighbors.
  - The goal is all lights off.
  - Generate the board by applying k random presses to a solved board. This guarantees it is solvable within k presses.
- **Timing:** 10 s.
- **Score:** if solved, `raw = 600 + 400 × (1 − elapsed/10 s) − 50 × max(0, moves − k)`. If unsolved, `raw = 300 × (lightsOffFraction)`.
- **Performance:** 1.0 if solved with 1.5k moves or fewer, 0.7 if solved, otherwise `0.4 × fraction`.
- **Difficulty knob:** k, and a 4×4 board at D 9-10 with k ≤ 3.

### 6.13 Cross-cutting recommendations

1. **Ship order:**
   - The 8 implemented types already cover speed, memory, attention, math and logic.
   - Add FLANKER and CHIMP_ORDER next: they are high value and need no color.
   - Then RULE_SWITCH and LIGHTS_OUT.
2. **Per-type staircase** at a target of 0.7, with asymmetric steps, faster calibration over the first 5 plays, and inactivity decay.
3. **Show** score, a concrete detail, the PB, and a delta against the 7-day median. Never show percentiles against other people, a "brain age" or a "cognitive score".
4. **Store** results locally:
   - DataStore JSON for progress.
   - Room only if a full history chart is wanted.
   - Record the device model with each result.
   - Exclude nothing sensitive, because the app collects nothing.
5. **Measure RT** from the stimulus frame time to the touch-down event time, both on the uptime base. Flag responses under 100 ms. Compare only within the same device.
6. **Copy:** "a 10-second game for your brain, every time you open your phone". Add the "not a medical device" disclaimer, and use no improvement or prevention claims.
7. **Habit:** a calendar-day streak with automatic rest tokens, an optional reminder, a Glance widget that deep-links to the challenge, and 3 app shortcuts. No ads between open and challenge, ever.
