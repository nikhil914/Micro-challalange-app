# MicroMind: 10-second brain challenges

Android app (Kotlin, Jetpack Compose). Every time you open it, you get one 10-second micro-challenge:
a memory test, reaction game or tiny puzzle. Built as a game for teens and adults (13+): XP, ranks, hot-run combos, streaks. Scores, streaks and levels are stored on the device in a TinyDB-style key/value store.

## Modules
- `core/`: pure Kotlin game logic. No Android dependency. Unit tested on the JVM.
- `app/`: Android UI (Compose Material3), SharedPreferences store, lifecycle handling.

## Build
Open the folder in Android Studio (Ladybug or newer), let it sync, then run `app`.

Command line:
```bash
./gradlew :app:assembleDebug
```

Core tests only (no Android SDK needed):
```bash
./gradlew :core:test -PcoreOnly=true
```

## Docs
- `docs/ROADMAP.md`: phases and milestones.
- `docs/CHALLENGE_CONTRACT.md`: how to add a new game.
- `docs/RESEARCH.md`: research notes (cognitive tasks, competitors, Android timing, App Inventor path).

## Disclaimer
MicroMind is a game. It is not a medical device and makes no claim to improve memory, intelligence or everyday abilities.
