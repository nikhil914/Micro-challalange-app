package com.microchallenge.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.microchallenge.app.ui.AppRoot
import com.microchallenge.app.ui.MicroMindTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            MicroMindTheme {
                AppRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() { super.onStart(); vm.onForeground() }

    override fun onStop() { vm.onBackground(); super.onStop() }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_DAILY -> vm.openDaily()
            ACTION_STATS -> vm.showStats()
        }
    }

    companion object {
        const val ACTION_DAILY = "com.microchallenge.app.DAILY"
        const val ACTION_STATS = "com.microchallenge.app.STATS"
    }
}
