package com.microchallenge.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.microchallenge.app.MainViewModel
import com.microchallenge.app.Screen

@Composable
fun AppRoot(vm: MainViewModel) {
    val screen = vm.screen
    BackHandler(enabled = screen is Screen.Stats || screen is Screen.Settings) { vm.newChallenge() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = screen,
            contentKey = { it::class },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
            label = "screen",
        ) { s ->
            when (s) {
                is Screen.Intro -> IntroScreen(s, vm.streakNow(), rankTitle = vm.rankTitle(), onStart = vm::start, onSkip = vm::newChallenge,
                    onStats = vm::showStats, onDaily = vm::openDaily, dailyDone = vm.repo.isDailyDone(vm.today()))
                is Screen.Playing -> ChallengeHost(s, hapticsOn = vm.hapticsOn, onFinished = vm::finish)
                is Screen.Result -> ResultScreen(s, onAgain = vm::newChallenge, onRetry = vm::retry, onStats = vm::showStats)
                Screen.Stats -> StatsScreen(vm.repo, vm.streakNow(), onBack = vm::newChallenge, onSettings = vm::showSettings)
                Screen.Settings -> SettingsScreen(vm.hapticsOn, vm::setHaptics, vm::resetProgress, onBack = vm::showStats)
            }
        }
    }
}
