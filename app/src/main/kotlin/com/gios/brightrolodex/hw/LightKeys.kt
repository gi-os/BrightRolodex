package com.gios.brightrolodex.hw

import android.view.KeyEvent

/**
 * The Light Phone III's physical controls, recognised from an ordinary [KeyEvent].
 *
 * Light patched `/system/usr/keylayout/Generic.kl` — which every input device on the phone
 * loads — so the wheel and the camera button arrive at the focused window as normal key
 * events. No root, no accessibility service: a sideloaded APK just gets them.
 *
 * Probed on LightPhoneIII / TLP301, Android 14, build `00WW_1_440000`:
 *
 * | Control                    | Linux scancode      | Keycode label |
 * |----------------------------|---------------------|---------------|
 * | Wheel one way              | 19 (`KEY_R`)        | `WHEEL_CCW`   |
 * | Wheel other way            | 20 (`KEY_T`)        | `WHEEL_CW`    |
 * | Wheel press (flashlight)   | 66 (`KEY_F8`)       | `WHEEL_CLICK` |
 * | Camera button, first stage | 80 (`KEY_KP2`)      | `FOCUS`       |
 * | Camera button, second      | 27 (`KEY_RIGHTBRACE`) | `CAMERA`    |
 *
 * `WHEEL_CW`, `WHEEL_CCW` and `WHEEL_CLICK` are not AOSP keycodes — Light added them — so
 * their numeric values are not guaranteed to stay put. Resolve them by name at first use and
 * fall back to the scancodes, which come from the hardware and are stable.
 *
 * The wheel is not a rotary encoder. It is a Pixart pat9126ja optical sensor that emits one
 * discrete DOWN + UP pair per notch, 35–60 ms apart, which is why [of] is only ever consulted
 * on ACTION_DOWN and why there is no key repeat to debounce.
 */
enum class LightKey {
    WheelUp,
    WheelDown,
    WheelClick,
    Camera,
    Focus,
}

object LightKeys {

    private const val SCAN_WHEEL_CCW = 19
    private const val SCAN_WHEEL_CW = 20
    private const val SCAN_WHEEL_CLICK = 66
    private const val SCAN_CAMERA = 27
    private const val SCAN_FOCUS = 80

    private val wheelCwCode: Int by lazy { codeOf("WHEEL_CW") }
    private val wheelCcwCode: Int by lazy { codeOf("WHEEL_CCW") }
    private val wheelClickCode: Int by lazy { codeOf("WHEEL_CLICK") }

    /**
     * `keyCodeFromString` returns [KeyEvent.KEYCODE_UNKNOWN] (0) for a label the platform does
     * not know, and 0 is also a perfectly ordinary keycode to receive. Map the miss to -1 so a
     * comparison against it can never match a real event on a phone that is not a Light.
     */
    private fun codeOf(label: String): Int {
        val code = runCatching { KeyEvent.keyCodeFromString(label) }
            .getOrDefault(KeyEvent.KEYCODE_UNKNOWN)
        return if (code == KeyEvent.KEYCODE_UNKNOWN) -1 else code
    }

    fun of(event: KeyEvent): LightKey? {
        when (event.keyCode) {
            wheelCwCode -> return LightKey.WheelUp
            wheelCcwCode -> return LightKey.WheelDown
            wheelClickCode -> return LightKey.WheelClick
        }
        return when (event.scanCode) {
            SCAN_WHEEL_CW -> LightKey.WheelUp
            SCAN_WHEEL_CCW -> LightKey.WheelDown
            SCAN_WHEEL_CLICK -> LightKey.WheelClick
            SCAN_CAMERA -> LightKey.Camera
            SCAN_FOCUS -> LightKey.Focus
            else -> null
        }
    }
}
