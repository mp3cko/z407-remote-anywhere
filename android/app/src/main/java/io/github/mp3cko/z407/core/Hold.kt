package io.github.mp3cko.z407.core

/**
 * Decides when the app holds the speakers: while it runs ([start] until
 * [stop]), unless another device asked for them ([release] until
 * [reconnect]) or the screen is off ([screen]).
 *
 * With the screen off Android suspends the CPU and puts Wi-Fi to sleep: the
 * HTTP server stops answering, while the Bluetooth chip keeps the link to
 * the speakers up and their keep-alives wake the phone just long enough to
 * answer them. The phone would hold the speakers where no other device can
 * ask for them, so it lets go of them, and the server sleeps with the
 * phone. Back on, the app looks for the speakers again, but quietly: it does
 * not ask a device that took them meanwhile to give them back.
 *
 * [connect] (with `quiet` = do not ask the other devices) and [disconnect]
 * are called under this object's lock, one at a time.
 */
class Hold(
    private val connect: (quiet: Boolean) -> Unit,
    private val disconnect: () -> Unit,
    private val log: (String) -> Unit = {},
    /** Whether the speakers are connected, or being connected to; else the app only looks for them. */
    private val holding: () -> Boolean = { true },
) {
    companion object {
        const val SLEEP_LOG = "Screen off: released the speakers so the phone can sleep and other devices can " +
            "take them. This app and its web address do not answer until the screen is on again"
        const val SLEEP_SEARCHING_LOG = "Screen off: stopped looking for the speakers so the phone can sleep. " +
            "This app and its web address do not answer until the screen is on again"
        const val WAKE_LOG = "Screen on: looking for the speakers again, without taking them from a device that has them"
    }

    /** Another device asked for the speakers and has them until the user reconnects. */
    @Volatile var released = false
        private set
    /** The screen is off, so the speakers are let go of. */
    @Volatile var asleep = false
        private set
    private var active = false

    /** The app runs: connects unless released or asleep. */
    @Synchronized
    fun start() {
        active = true
        if (!released && !asleep) connect(false)
    }

    /** The app was closed: lets go; opened again it connects, also after a release. */
    @Synchronized
    fun stop() {
        active = false
        released = false
        disconnect()
    }

    /** POST /api/disconnect: lets go of the speakers until the user reconnects. */
    @Synchronized
    fun release() {
        if (!active || released) return // the app was closed meanwhile, or was asked twice
        released = true
        log("Another device asked for the speakers: disconnected until you reconnect")
        disconnect()
    }

    /** POST /api/reconnect: takes the speakers back, asking the other devices for them again. */
    @Synchronized
    fun reconnect() {
        if (!released) return
        released = false
        log("Reconnecting")
        if (active && !asleep) connect(false)
    }

    /**
     * Follows the screen. [on] is read under the lock, so calls that
     * overtake each other still end in the screen's latest state.
     */
    @Synchronized
    fun screen(on: () -> Boolean) {
        if (on()) wake() else sleep()
    }

    private fun sleep() {
        if (asleep) return
        asleep = true
        if (!active || released) return
        log(if (holding()) SLEEP_LOG else SLEEP_SEARCHING_LOG)
        disconnect()
    }

    private fun wake() {
        if (!asleep) return
        asleep = false
        if (!active || released) return
        log(WAKE_LOG)
        connect(true)
    }
}
