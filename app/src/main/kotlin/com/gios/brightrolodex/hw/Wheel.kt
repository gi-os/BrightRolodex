package com.gios.brightrolodex.hw

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * A bus of wheel notches, published by the activity and collected by whatever screen is up.
 *
 * The activity is the only place that can see these events: a focused child view eats key
 * events before any Compose handler runs, so `dispatchKeyEvent` at the window is the hook and
 * a bus is how the value reaches composables that come and go.
 *
 * `extraBufferCapacity` with DROP_OLDEST rather than a plain `MutableSharedFlow()`: a
 * zero-buffer SharedFlow with no collector *suspends* the emitter, and the emitter here is
 * `dispatchKeyEvent` on the main thread. A notch arriving while nothing is listening should be
 * discarded, not block input.
 */
class WheelBus {
    private val _notches = MutableSharedFlow<Int>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val notches: SharedFlow<Int> = _notches

    /** +1 for one notch up, -1 for one notch down. */
    fun send(delta: Int) {
        _notches.tryEmit(delta)
    }
}

val LocalWheelBus = staticCompositionLocalOf { WheelBus() }

/**
 * Scrolls [state] by one screenful fraction per notch.
 *
 * `LaunchedEffect(state, bus)` and not `LaunchedEffect(onNotch)`: keying a collector on a
 * lambda restarts it on every recomposition, which drops notches for as long as the screen is
 * being redrawn — the exact moment the user is turning the wheel.
 */
@Composable
fun WheelScroll(state: ScrollableState, pixelsPerNotch: Float = 220f) {
    val bus = LocalWheelBus.current
    LaunchedEffect(state, bus) {
        bus.notches.collect { delta ->
            // Negated: a notch "up" should move the content up, i.e. scroll forward.
            state.animateScrollBy(-delta * pixelsPerNotch)
        }
    }
}

/**
 * The wheel as a discrete stepper, for things that are not a scrolling list — flipping the
 * deck, cycling a parts variant, changing zoom.
 *
 * [rememberUpdatedState] rather than keying the effect on [onStep]: the collector must survive
 * recomposition (a restart drops notches for exactly as long as the screen is being redrawn,
 * which is while the wheel is turning) but the lambda it calls has to be the current one, or a
 * stepper closes over the mode the screen was in when it first appeared.
 */
@Composable
fun WheelSteps(onStep: (Int) -> Unit) {
    val bus = LocalWheelBus.current
    val current by rememberUpdatedState(onStep)
    LaunchedEffect(bus) {
        bus.notches.collect { delta -> current(delta) }
    }
}
