package io.github.mp3cko.z407.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldTest {
    private val calls = ArrayList<String>()
    private val log = ArrayList<String>()
    private var holding = true
    private val hold = Hold(
        connect = { quiet -> calls += if (quiet) "connect quietly" else "connect" },
        disconnect = { calls += "disconnect" },
        log = { log += it },
        holding = { holding },
    )
    private var screenOn = true

    private fun screen(on: Boolean) {
        screenOn = on
        hold.screen { screenOn }
    }

    @Test
    fun startConnects() {
        hold.start()
        assertEquals(listOf("connect"), calls)
    }

    @Test
    fun screenOffLetsGoAndSaysWhy() {
        hold.start()
        screen(false)
        assertEquals(listOf("connect", "disconnect"), calls)
        assertTrue(hold.asleep)
        assertEquals(listOf(Hold.SLEEP_LOG), log)
    }

    @Test
    fun screenOffWhileOnlyLookingSaysSo() {
        hold.start()
        holding = false
        screen(false)
        assertEquals(listOf("connect", "disconnect"), calls)
        assertEquals(listOf(Hold.SLEEP_SEARCHING_LOG), log)
    }

    @Test
    fun screenOnLooksAgainWithoutAskingOthers() {
        hold.start()
        screen(false)
        screen(true)
        assertEquals(listOf("connect", "disconnect", "connect quietly"), calls)
        assertFalse(hold.asleep)
        assertEquals(listOf(Hold.SLEEP_LOG, Hold.WAKE_LOG), log)
    }

    @Test
    fun repeatedScreenEventsDoNothingMore() {
        hold.start()
        screen(true)
        screen(false)
        screen(false)
        screen(true)
        screen(true)
        assertEquals(listOf("connect", "disconnect", "connect quietly"), calls)
    }

    @Test
    fun theScreenIsReadUnderTheLock() {
        hold.start()
        screenOn = false
        // A call made for "on" that runs after the screen went off follows the screen.
        hold.screen { screenOn }
        assertTrue(hold.asleep)
        assertEquals(listOf("connect", "disconnect"), calls)
    }

    @Test
    fun startedWithTheScreenOffDoesNotConnect() {
        screen(false)
        hold.start()
        assertEquals(emptyList<String>(), calls)
        assertEquals(emptyList<String>(), log) // nothing was held, nothing to explain
        screen(true)
        assertEquals(listOf("connect quietly"), calls)
    }

    @Test
    fun notRunningIgnoresTheScreen() {
        screen(false)
        screen(true)
        assertEquals(emptyList<String>(), calls)
        hold.start()
        hold.stop()
        calls.clear()
        screen(false)
        screen(true)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun releasedStaysReleasedThroughTheScreen() {
        hold.start()
        hold.release()
        screen(false)
        screen(true)
        assertEquals(listOf("connect", "disconnect"), calls)
        assertTrue(hold.released)
        hold.reconnect()
        assertEquals(listOf("connect", "disconnect", "connect"), calls)
    }

    @Test
    fun releaseWhileAsleepHoldsNothingAndWaitsForReconnect() {
        hold.start()
        screen(false)
        hold.release()
        assertTrue(hold.released)
        screen(true)
        assertEquals(listOf("connect", "disconnect", "disconnect"), calls)
        hold.reconnect()
        assertEquals(listOf("connect", "disconnect", "disconnect", "connect"), calls)
    }

    @Test
    fun reconnectWhileAsleepWaitsForTheScreen() {
        hold.start()
        hold.release()
        screen(false)
        hold.reconnect()
        assertFalse(hold.released)
        assertEquals(listOf("connect", "disconnect"), calls)
        screen(true)
        assertEquals(listOf("connect", "disconnect", "connect quietly"), calls)
    }

    @Test
    fun releaseOnlyOnceAndOnlyWhileRunning() {
        hold.release()
        assertFalse(hold.released)
        hold.start()
        hold.release()
        hold.release()
        assertEquals(listOf("connect", "disconnect"), calls)
    }

    @Test
    fun stopForgetsTheReleaseButNotTheScreen() {
        hold.start()
        hold.release()
        screen(false)
        hold.stop()
        assertFalse(hold.released)
        calls.clear()
        hold.start() // restarted while the screen is still off
        assertEquals(emptyList<String>(), calls)
        screen(true)
        assertEquals(listOf("connect quietly"), calls)
    }
}
