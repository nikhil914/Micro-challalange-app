# Challenge contract (read before adding a game)

Core (pure Kotlin, `core/src/main/kotlin/com/microchallenge/core/`):
- `model/ChallengeType.kt` — enum of all games.
- `challenges/ChallengeSession.kt` — `ChallengeSession` interface, `Scoring` helpers, difficulty 1..10.
- `challenges/ChallengeRegistry.kt` — maps type to constructor `XSession(difficulty: Int, random: Random, clock: Clock)`.
- `engine/Clock.kt` — `Clock` (monotonicMs, wallMs), `FakeClock` for tests.
- `model/ChallengeResult.kt` — score 0..1000, performance 0..1, `CHALLENGE_DURATION_MS = 10_000`.

Each game session:
- Lives in package `com.microchallenge.core.challenges.games`, file `XSession.kt`.
- Is a deterministic state machine given the `Random` seed. No Android, no threads, no coroutines.
- Reads time only via `clock.monotonicMs()`. Captures its start time in the constructor (host builds it the moment the 10 s window starts). The host's clock pauses while app is backgrounded.
- Exposes input methods the UI calls (e.g. `tap(index)`), plus read-only state the UI renders. Methods that depend on time compute phase from `clock.monotonicMs() - startMs`.
- `isComplete` true when the run ended before 10 s (e.g. memory answer submitted, false start).
- `finish()` returns `ChallengeResult` with score 0..1000 (use `Scoring`), performance 0..1 (~0.7 = "good, appropriately hard"), `durationMs` = elapsed, short human `detail` (e.g. "Avg 312 ms").
- Must be safe if `finish()` is called at 10 s without any input (score 0, performance 0).
- Difficulty knob scales across 1..10 meaningfully.

Tests: `core/src/test/kotlin/com/microchallenge/core/games/XSessionTest.kt`, JUnit4 + kotlin.test, use `FakeClock`.

UI (Android, Jetpack Compose Material3, `app/src/main/java/com/microchallenge/app/games/XScreen.kt`):
```kotlin
@Composable
fun XScreen(session: XSession, elapsedMs: Long, onInput: () -> Unit)
```
- `elapsedMs` updates every frame; read it so the screen recomposes. Call `onInput()` after every mutation of the session.
- Fill available space (`Modifier.fillMaxSize()`); host draws the title + timer bar above.
- Large touch targets (>=64dp), never rely on color alone (add shape/label), content descriptions for TalkBack.
- Colors: use `MaterialTheme.colorScheme` plus `com.microchallenge.app.ui.GameColors` (red, green, blue, yellow, purple, orange — each has `.color` and `.label`).
- Use `Modifier.pointerInput { detectTapGestures(onPress = ...) }` for reaction-critical taps (fires on touch down, not release).
