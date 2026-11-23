package io.github.mp3cko.z407.core

/**
 * Tells the user opening the app from the screen coming back on with the app
 * already on it. Only an opening asks the other devices for the speakers: the
 * screen going off let go of them, and a device that took them meanwhile
 * keeps them (see [Hold]).
 */
class Visits {
    private var hiddenBySleep = false

    /** The app left the screen; [screenOn] is false when the screen going off hid it. */
    @Synchronized
    fun stopped(screenOn: Boolean) {
        hiddenBySleep = !screenOn
    }

    /** The app came to the screen: true when the user opened it, false when the screen came back on. */
    @Synchronized
    fun started(): Boolean {
        val opened = !hiddenBySleep
        hiddenBySleep = false
        return opened
    }
}
