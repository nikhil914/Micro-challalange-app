package com.microchallenge.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.microchallenge.app.MainViewModel
import com.microchallenge.app.Screen
import com.microchallenge.app.ui.design.GlowBackground
import com.microchallenge.app.ui.design.Palette
import com.microchallenge.app.ui.design.style
import com.microchallenge.core.engine.Progression
import com.microchallenge.core.model.ChallengeType

@Composable
fun AppRoot(vm: MainViewModel) {
    val screen = vm.screen
    BackHandler(enabled = screen !is Screen.Home && screen !is Screen.Playing) { vm.goBack() }
    val accent = when (screen) {
        is Screen.Intro -> screen.type.skill.style().start
        is Screen.Playing -> screen.session.type.skill.style().start
        is Screen.Result -> screen.result.type.skill.style().start
        else -> Palette.Primary
    }
    val onTab = screen is Screen.Home || screen is Screen.Library || screen is Screen.Stats

    val haptics = if (vm.hapticsOn) LocalHapticFeedback.current else SilentHaptics
    CompositionLocalProvider(LocalHapticFeedback provides haptics) {
        Box(Modifier.fillMaxSize()) {
            GlowBackground(accent = accent, still = screen is Screen.Playing)
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                AnimatedContent(
                    targetState = screen,
                    contentKey = { it::class },
                    transitionSpec = { (fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 0.96f)) togetherWith fadeOut(tween(140)) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    label = "screen",
                ) { s ->
                    when (s) {
                        Screen.Home -> HomeScreen(
                            homeState(vm), onDaily = vm::openDaily, onQuickPlay = vm::newChallenge,
                            onGame = vm::openGame, onSeeAll = vm::showLibrary,
                        )
                        Screen.Library -> LibraryScreen(
                            bests = ChallengeType.entries.associateWith { vm.repo.best(it) },
                            plays = vm.repo.playCounts(),
                            filter = vm.libraryFilter, onFilter = vm::pickLibraryFilter, onGame = vm::openGame,
                        )
                        is Screen.Intro -> IntroScreen(
                            s, best = vm.repo.best(s.type), plays = vm.repo.plays(s.type),
                            onStart = vm::start, onBack = vm::goBack, onShuffle = vm::newChallenge,
                        )
                        is Screen.Playing -> Box(Modifier.padding(16.dp)) {
                            ChallengeHost(s, hapticsOn = vm.hapticsOn, onFinished = vm::finish)
                        }
                        is Screen.Result -> Box(Modifier.padding(16.dp)) {
                            ResultScreen(s, onAgain = vm::newChallenge, onRetry = vm::retry, onHome = vm::goBack)
                        }
                        Screen.Stats -> StatsScreen(vm.repo, vm.streakNow(), vm.today(), onSettings = vm::showSettings)
                        Screen.Settings -> SettingsScreen(
                            hapticsOn = vm.hapticsOn, onHaptics = vm::setHaptics,
                            reduceMotion = vm.reduceMotion, onReduceMotion = vm::setMotionReduced,
                            autoStart = vm.autoStartOnOpen, onAutoStart = vm::setAutoStart,
                            onReset = vm::resetProgress, onBack = vm::goBack,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = onTab,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    BottomBar(vm.tab, vm::showTab)
                }
            }
        }
    }
}

/** Used app-wide when the user turns vibration off. */
private object SilentHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

private fun homeState(vm: MainViewModel): HomeState {
    val xp = vm.repo.xp()
    val today = vm.today()
    return HomeState(
        rankTitle = vm.rankTitle(),
        rankProgress = Progression.rankProgress(xp),
        xp = xp,
        xpToNext = Progression.nextRank(xp)?.let { it.minXp - xp },
        streak = vm.streakNow(),
        playsToday = vm.playsToday(),
        dailyGoal = MainViewModel.DAILY_GOAL,
        dailyType = vm.dailyType(),
        dailyScore = vm.repo.dailyScore(today),
        recent = vm.recentGames(),
        bests = ChallengeType.entries.associateWith { vm.repo.best(it) },
    )
}
