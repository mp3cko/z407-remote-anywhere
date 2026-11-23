package io.github.mp3cko.z407.core

/** What the notification says while the app works in the background. */
data class Summary(val title: String, val text: String) {
    companion object {
        /**
         * [released]: another device asked for the speakers and has them.
         * [asleep]: the screen is off, so the app let go of the speakers.
         */
        fun of(status: Status, released: Boolean, asleep: Boolean = false): Summary = when {
            released -> Summary("Disconnected", "Another device took the speakers. Open the app to reconnect.")
            asleep -> Summary(
                "Released while the screen is off",
                "The phone may sleep, so other devices can take the speakers. Turn the screen on to reconnect.",
            )
            status.connected -> Summary("Connected", "Volume about ${status.volume}")
            status.phase == SpeakerManager.PHASE_SEARCHING ->
                Summary("Searching…", status.problem.ifEmpty { "Looking for the speakers" })
            status.phase == SpeakerManager.PHASE_CONNECTING -> Summary("Connecting…", "Speakers found")
            else -> Summary("Not connected", status.problem.ifEmpty { "Waiting for the next attempt" })
        }
    }
}
