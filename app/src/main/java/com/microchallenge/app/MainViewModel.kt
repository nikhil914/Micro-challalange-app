package com.microchallenge.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.microchallenge.app.data.AndroidClock
import com.microchallenge.app.data.PrefsTinyDb
import com.microchallenge.core.challenges.ChallengeRegistry
import com.microchallenge.core.challenges.ChallengeSession
import com.microchallenge.core.engine.ChallengePicker
import com.microchallenge.core.engine.Countdown
import com.microchallenge.core.engine.PausableClock
import com.microchallenge.core.engine.Progression
import com.microchallenge.core.engine.Rank
import com.microchallenge.core.engine.XpGain
import com.microchallenge.core.engine.StreakTracker
import com.microchallenge.core.model.ChallengeResult
import com.microchallenge.core.model.ChallengeType
import com.microchallenge.core.model.Skill
import com.microchallenge.core.store.ProgressRepository
import java.time.LocalDate
import kotlin.random.Random

/** The three bottom-bar destinations. */
enum class Tab { PLAY, GAMES, PROGRESS }

/** What the single-activity UI is showing. */
sealed interface Screen {
    /** Play tab: daily challenge, quick play, daily goal and game rows. */
    data object Home : Screen
    /** Games tab: every game in a grid, filterable by skill. */
    data object Library : Screen
    data class Intro(
        val type: ChallengeType,
        val difficulty: Int,
        val isDaily: Boolean,
        /** Skip the PLAY button and go straight to 3-2-1 (used by Retry). */
        val autoStart: Boolean = false,
        /** Picked at random by Quick Play, so the intro offers "Try another". */
        val random: Boolean = false,
    ) : Screen
    data class Playing(
        val session: ChallengeSession,
        val countdown: Countdown,
        val isDaily: Boolean,
    ) : Screen
    data class Result(
        val result: ChallengeResult,
        val isBest: Boolean,
        val previousBest: Int,
        val streak: Int,
        val isDaily: Boolean,
        val xpGain: XpGain,
        val xpTotal: Int,
        val rank: Rank,
        val rankedUp: Boolean,
        val combo: Int,
        /** Streak length just reached if it is a milestone (3, 7, 14...), else 0. */
        val streakMilestone: Int = 0,
    ) : Screen
    /** Progress tab. */
    data object Stats : Screen
    data object Settings : Screen
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = PrefsTinyDb(app)
    val repo = ProgressRepository(db)
    private var clock = PausableClock(AndroidClock)
    private var backgroundedAtMs = -1L

    var screen: Screen by mutableStateOf(if (db.getBoolean(K_AUTO_START, false)) nextIntro() else Screen.Home)
        private set

    /** Bottom-bar tab the user is on; leaving a game returns here. */
    var tab: Tab by mutableStateOf(Tab.PLAY)
        private set

    /** Skill filter on the Games tab; null shows every game. */
    var libraryFilter: Skill? by mutableStateOf(null)
        private set

    var hapticsOn: Boolean by mutableStateOf(db.getBoolean(K_HAPTICS, true))
        private set

    var reduceMotion: Boolean by mutableStateOf(db.getBoolean(K_REDUCE_MOTION, false))
        private set

    /** Start a random challenge straight away when the app opens (the original one-tap loop). */
    var autoStartOnOpen: Boolean by mutableStateOf(db.getBoolean(K_AUTO_START, false))
        private set

    fun today(): Long = LocalDate.now().toEpochDay()

    fun rankTitle(): String = Progression.rankFor(repo.xp()).title

    fun streakNow(): Int = StreakTracker.displayed(repo.streak(), today())

    fun playsToday(): Int = repo.history().count { it.day == today() }

    fun dailyType(): ChallengeType = ChallengePicker.dailyType(today())

    /** Last games played, newest first, without repeats. */
    fun recentGames(): List<ChallengeType> = repo.recentTypes().asReversed().distinct()

    private fun nextIntro(): Screen.Intro {
        val type = ChallengePicker.pick(repo.recentTypes(), repo.playCounts(), Random.Default)
        return Screen.Intro(type, repo.nextDifficulty(type), isDaily = false, random = true)
    }

    /** Quick Play: a random game picked for variety. */
    fun newChallenge() { screen = nextIntro() }

    fun openGame(type: ChallengeType) {
        screen = Screen.Intro(type, repo.nextDifficulty(type), isDaily = false)
    }

    fun showTab(t: Tab) {
        tab = t
        screen = when (t) {
            Tab.PLAY -> Screen.Home
            Tab.GAMES -> Screen.Library
            Tab.PROGRESS -> Screen.Stats
        }
    }

    fun showLibrary(filter: Skill?) {
        libraryFilter = filter
        showTab(Tab.GAMES)
    }

    fun pickLibraryFilter(filter: Skill?) { libraryFilter = filter }

    /** Leave an intro or result and go back to the tab the user came from. */
    fun goBack() {
        when (screen) {
            Screen.Settings -> showTab(Tab.PROGRESS)
            Screen.Library, Screen.Stats -> showTab(Tab.PLAY)
            is Screen.Playing, Screen.Home -> Unit
            else -> showTab(tab)
        }
    }

    /** Replay the game just finished, at its updated level. A daily retry is a normal (non-daily) play. */
    fun retry() {
        val type = (screen as? Screen.Result)?.result?.type ?: return
        screen = Screen.Intro(type, repo.nextDifficulty(type), isDaily = false, autoStart = true)
    }

    fun openDaily() {
        val day = today()
        if (repo.isDailyDone(day)) { showTab(Tab.PLAY); return }
        val type = ChallengePicker.dailyType(day)
        // Daily uses a fixed mid difficulty so scores are comparable between players.
        screen = Screen.Intro(type, DAILY_DIFFICULTY, isDaily = true)
    }

    fun start() {
        val intro = screen as? Screen.Intro ?: return
        clock = PausableClock(AndroidClock)
        val random = if (intro.isDaily) Random(ChallengePicker.dailySeed(today())) else Random.Default
        val session = ChallengeRegistry.create(intro.type, intro.difficulty, random, clock)
        val countdown = Countdown(clock).also { it.start() }
        screen = Screen.Playing(session, countdown, intro.isDaily)
    }

    /** Called by the host when time is up or the session completes early. */
    fun finish() {
        val playing = screen as? Screen.Playing ?: return
        val result = playing.session.finish().copy(timestampMs = AndroidClock.wallMs())
        val prevBest = repo.best(result.type)
        val prevRank = Progression.rankFor(repo.xp())
        val streakBefore = streakNow()
        val isBest = repo.record(result, today(), playing.isDaily)
        val xp = repo.xp()
        val rank = Progression.rankFor(xp)
        val streakAfter = streakNow()
        screen = Screen.Result(
            result, isBest, prevBest, streakNow(), playing.isDaily,
            xpGain = repo.lastXpGain, xpTotal = xp, rank = rank,
            rankedUp = rank.ordinal > prevRank.ordinal, combo = repo.combo(),
            streakMilestone = if (streakAfter > streakBefore && streakAfter in STREAK_MILESTONES) streakAfter else 0,
        )
    }

    fun showStats() { showTab(Tab.PROGRESS) }
    fun showSettings() { screen = Screen.Settings }

    fun setHaptics(on: Boolean) { hapticsOn = on; db.putBoolean(K_HAPTICS, on) }

    fun setMotionReduced(on: Boolean) { reduceMotion = on; db.putBoolean(K_REDUCE_MOTION, on) }

    fun setAutoStart(on: Boolean) { autoStartOnOpen = on; db.putBoolean(K_AUTO_START, on) }

    fun resetProgress() {
        repo.reset()
        db.putBoolean(K_HAPTICS, hapticsOn)
        db.putBoolean(K_REDUCE_MOTION, reduceMotion)
        db.putBoolean(K_AUTO_START, autoStartOnOpen)
        screen = Screen.Settings
    }

    /** Activity onStop: freeze challenge time. */
    fun onBackground() {
        clock.pause()
        backgroundedAtMs = AndroidClock.monotonicMs()
    }

    /**
     * Activity onStart. After a real break (not a quick app switch under
     * [REOPEN_THRESHOLD_MS]) the app reopens on the Play tab, or straight into a
     * fresh challenge when "Start a challenge on open" is on.
     */
    fun onForeground() {
        val away = if (backgroundedAtMs < 0) 0 else AndroidClock.monotonicMs() - backgroundedAtMs
        backgroundedAtMs = -1L
        when (screen) {
            is Screen.Playing -> clock.resume()
            is Screen.Result, Screen.Stats, Screen.Settings, Screen.Library, Screen.Home ->
                if (away >= REOPEN_THRESHOLD_MS) { if (autoStartOnOpen) newChallenge() else showTab(Tab.PLAY) }
            is Screen.Intro -> Unit
        }
    }

    companion object {
        const val DAILY_DIFFICULTY = 5
        const val REOPEN_THRESHOLD_MS = 30_000L
        /** Rounds per day that fill the daily goal ring on the Play tab. */
        const val DAILY_GOAL = 3
        val STREAK_MILESTONES = setOf(3, 7, 14, 30, 50, 100, 200, 365)
        private const val K_HAPTICS = "setting_haptics"
        private const val K_REDUCE_MOTION = "setting_reduce_motion"
        private const val K_AUTO_START = "setting_auto_start"
    }
}
