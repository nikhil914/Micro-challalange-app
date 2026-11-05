package com.microchallenge.app.data

import android.os.SystemClock
import com.microchallenge.core.engine.Clock

/** Monotonic time from elapsedRealtimeNanos: immune to wall-clock changes, keeps counting in deep sleep. */
object AndroidClock : Clock {
    override fun monotonicMs(): Long = SystemClock.elapsedRealtimeNanos() / 1_000_000
    override fun wallMs(): Long = System.currentTimeMillis()
}
