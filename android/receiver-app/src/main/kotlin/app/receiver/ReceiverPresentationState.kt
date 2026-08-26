package app.receiver

/** Tracks whether the Receiver activity is visible without creating a background service. */
object ReceiverPresentationState {
    @Volatile private var foreground = false

    fun markForeground(value: Boolean) {
        foreground = value
    }

    fun isForeground(): Boolean = foreground
}
