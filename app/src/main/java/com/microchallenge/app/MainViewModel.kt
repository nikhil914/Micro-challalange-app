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
import com.microchallenge.core.store.ProgressRepository
import java.time.LocalDate
import kotlin.random.Random

/** What the single-activity UI is showing. */
sealed interface Screen {
    data class Intro(
        val type: ChallengeType,
        val difficulty: Int,
        val isDaily: Boolean,
        /** Skip the PLAY button and go straight to 3-2-1 (used by Retry). */
        val autoStart: Boolean = false,
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
    ) : Screen
    data object Stats : Screen
    data object Settings : Screen
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = PrefsTinyDb(app)
    val repo = ProgressRepository(db)
    private var clock = PausableClock(AndroidClock)
    private var backgroundedAtMs = -1L

    var screen: Screen by mutableStateOf(nextIntro())
        private set

    var hapticsOn: Boolean by mutableStateOf(db.getBoolean(K_HAPTICS, true))
        private set

    fun today(): Long = LocalDate.now().toEpochDay()

    fun rankTitle(): String = Progression.rankFor(repo.xp()).title

    fun streakNow(): Int = StreakTracker.displayed(repo.streak(), today())

    private fun nextIntro(): Screen.Intro {
        val type = ChallengePicker.pick(repo.recentTypes(), repo.playCounts(), Random.Default)
        return Screen.Intro(type, repo.nextDifficulty(type), isDaily = false)
    }

    fun newChallenge() { screen = nextIntro() }

    /** Replay the game just finished, at its updated level. A daily retry is a normal (non-daily) play. */
    fun retry() {
        val type = (screen as? Screen.Result)?.result?.type ?: return
        screen = Screen.Intro(type, repo.nextDifficulty(type), isDaily = false, autoStart = true)
    }

    fun openDaily() {
        val day = today()
        if (repo.isDailyDone(day)) { screen = Screen.Stats; return }
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
        val isBest = repo.record(result, today(), playing.isDaily)
        val xp = repo.xp()
        val rank = Progression.rankFor(xp)
        screen = Screen.Result(
            result, isBest, prevBest, streakNow(), playing.isDaily,
            xpGain = repo.lastXpGain, xpTotal = xp, rank = rank,
            rankedUp = rank.ordinal > prevRank.ordinal, combo = repo.combo(),
        )
    }

    fun showStats() { screen = Screen.Stats }
    fun showSettings() { screen = Screen.Settings }

    fun setHaptics(on: Boolean) { hapticsOn = on; db.putBoolean(K_HAPTICS, on) }

    fun resetProgress() {
        repo.reset()
        db.putBoolean(K_HAPTICS, hapticsOn)
        screen = Screen.Settings
    }

    /** Activity onStop: freeze challenge time. */
    fun onBackground() {
        clock.pause()
        backgroundedAtMs = AndroidClock.monotonicMs()
    }

    /**
     * Activity onStart. Core promise of the app: every open gives a fresh
     * micro-challenge. A quick app switch (< [REOPEN_THRESHOLD_MS]) resumes instead.
     */
    fun onForeground() {
        val away = if (backgroundedAtMs < 0) 0 else AndroidClock.monotonicMs() - backgroundedAtMs
        backgroundedAtMs = -1L
        when (screen) {
            is Screen.Playing -> clock.resume()
            is Screen.Result, Screen.Stats, Screen.Settings ->
                if (away >= REOPEN_THRESHOLD_MS) newChallenge()
            is Screen.Intro -> Unit
        }
    }

    companion object {
        const val DAILY_DIFFICULTY = 5
        const val REOPEN_THRESHOLD_MS = 30_000L
        private const val K_HAPTICS = "setting_haptics"
    }
}
