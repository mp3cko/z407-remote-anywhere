package io.github.mp3cko.z407.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitsTest {
    private val visits = Visits()

    @Test
    fun firstStartIsAnOpening() {
        assertTrue(visits.started())
    }

    @Test
    fun comingBackFromAnotherAppIsAnOpening() {
        visits.started()
        visits.stopped(screenOn = true)
        assertTrue(visits.started())
    }

    @Test
    fun theScreenComingBackOnIsNot() {
        visits.started()
        visits.stopped(screenOn = false)
        assertFalse(visits.started())
    }

    @Test
    fun onlyTheVisitRightAfterTheScreenWentOff() {
        visits.started()
        visits.stopped(screenOn = false)
        visits.started()
        visits.stopped(screenOn = true) // then left for another app, and back
        assertTrue(visits.started())
    }

    @Test
    fun theLastStopCounts() {
        visits.stopped(screenOn = false)
        visits.stopped(screenOn = true)
        assertTrue(visits.started())
    }
}
