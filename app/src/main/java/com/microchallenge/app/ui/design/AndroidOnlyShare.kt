package com.microchallenge.app.ui.design

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Returns a function that opens the Android share sheet with [text]. */
@Composable
fun rememberShareText(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text: String ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
