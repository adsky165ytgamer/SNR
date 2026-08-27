package app.receiver

/** Tracks whether the Receiver activity is visible without creating a background service. */
object ReceiverPresentationState {
    @Volatile private var foreground = false
    @Volatile private var windowFocused = false

    fun markForeground(value: Boolean) {
        foreground = value
        if (!value) windowFocused = false
    }

    fun isForeground(): Boolean = foreground

    fun markWindowFocused(value: Boolean) {
        windowFocused = value
    }

    fun shouldPresentOverlay(): Boolean = !foreground || !windowFocused
}
