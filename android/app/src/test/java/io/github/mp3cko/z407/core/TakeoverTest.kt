package io.github.mp3cko.z407.core

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors internal/peers/takeover_test.go, with a fake clock. */
class TakeoverTest {
    private var now = 1_000_000L
    private var connected = false
    private var peers = listOf("desk:5000")
    private val unreachable = HashSet<String>()
    private val calls: MutableList<String> = java.util.Collections.synchronizedList(ArrayList())
    private val log = ArrayList<String>()
    private var found = false
    private val notes = ArrayList<String>()
    private val timeout = Takeover.SEARCH_TIMEOUT_MILLIS

    private val takeover = Takeover(
        peers = { peers },
        connected = { connected },
        post = { peer ->
            calls.add(peer)
            if (peer in unreachable) throw IOException("connection refused")
            "200 {\"success\":true}"
        },
        log = { log.add(it) },
        found = { found },
        note = { notes.add(it) },
    )

    private fun advance(millis: Long) {
        now += millis
        takeover.check(now)
    }

    @Test
    fun firstRoundIsAtOnce() {
        advance(0)
        assertEquals(listOf("desk:5000"), calls)
    }

    @Test
    fun laterPeerWaitsForTheTimeout() {
        peers = emptyList()
        advance(0)
        peers = listOf("desk:5000")
        advance(timeout - 1)
        assertEquals(emptyList<String>(), calls)
        advance(1)
        assertEquals(listOf("desk:5000"), calls)
    }

    @Test
    fun seenSpeakersGetMoreTime() {
        peers = emptyList()
        found = true
        advance(0)
        peers = listOf("desk:5000")
        advance(Takeover.CONNECT_TIMEOUT_MILLIS - 1)
        assertEquals(emptyList<String>(), calls)
        advance(1) // seen, but the connection never came
        assertEquals(listOf("desk:5000"), calls)
    }

    @Test
    fun notesSumUpARound() {
        peers = listOf("desk:5000", "attic:5000")
        unreachable.add("attic:5000")
        advance(0)
        assertEquals(listOf("Asking 2 other devices to release the speakers", "desk:5000 released the speakers"), notes)
        notes.clear()
        takeover.newSession()
        peers = listOf("attic:5000")
        advance(0)
        assertEquals(listOf("Asking attic:5000 to release the speakers", "attic:5000 did not answer"), notes)
    }

    @Test
    fun outcomeText() {
        val p = listOf("a:5000", "b:5000", "c:5000")
        assertEquals("a:5000 and c:5000 released the speakers", Takeover.outcomeText(p, intArrayOf(0, 2, 0)))
        assertEquals("a:5000, b:5000 and c:5000 released the speakers", Takeover.outcomeText(p, intArrayOf(0, 0, 0)))
        assertEquals("b:5000 did not release the speakers", Takeover.outcomeText(p, intArrayOf(2, 1, 2)))
        assertEquals("No other device answered", Takeover.outcomeText(p, intArrayOf(2, 2, 2)))
        // Answered, but had nothing to give: named after those who refused.
        assertEquals("a:5000 and b:5000 did not have the speakers", Takeover.outcomeText(p, intArrayOf(3, 3, 2)))
        assertEquals("c:5000 did not release the speakers", Takeover.outcomeText(p, intArrayOf(3, 2, 1)))
        assertEquals("b:5000 released the speakers", Takeover.outcomeText(p, intArrayOf(3, 0, 1)))
    }

    @Test
    fun outcomeOfAnAnswer() {
        assertEquals(0, Takeover.outcomeOf("200 {\"success\":true}"))
        assertEquals(0, Takeover.outcomeOf("200 ok"))
        // Let go already, or still looking: nothing to give.
        assertEquals(3, Takeover.outcomeOf("200 {\"held\":false,\"success\":true}"))
        assertEquals(3, Takeover.outcomeOf("200 {\"held\": false, \"success\": true}"))
        assertEquals(3, Takeover.outcomeOf("409 {\"error\":\"This device does not hold the speakers\",\"success\":false}"))
        assertEquals(1, Takeover.outcomeOf("501 {\"success\":false}"))
        assertEquals(1, Takeover.outcomeOf("404 page not found"))
    }

    @Test
    fun peersAreAskedAtTheSameTime() {
        // Every request waits until all three have come in: asked one after
        // another, the first would never return.
        val latch = java.util.concurrent.CountDownLatch(3)
        val asked = java.util.Collections.synchronizedList(ArrayList<String>())
        val t = Takeover(
            peers = { listOf("a:5000", "b:5000", "c:5000") },
            connected = { false },
            post = { peer ->
                latch.countDown()
                assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS))
                asked.add(peer)
                "200 {\"success\":true}"
            },
        )
        t.check(0)
        assertEquals(setOf("a:5000", "b:5000", "c:5000"), asked.toSet())
    }

    @Test
    fun noCallWhileConnected() {
        connected = true
        advance(0)
        advance(10 * timeout)
        assertEquals(emptyList<String>(), calls)
        // The first round is at once, also after a drop.
        connected = false
        advance(1000)
        assertEquals(listOf("desk:5000"), calls)

        // A later drop: a new peer waits; the time spent connected does not count.
        connected = true
        advance(1000)
        peers = listOf("desk:5000", "new:5000")
        advance(10 * timeout)
        connected = false
        advance(1000)
        advance(timeout - 1000)
        assertEquals(listOf("desk:5000"), calls)
        // Connecting in time cancels it.
        connected = true
        advance(1000)
        connected = false
        advance(1000)
        advance(timeout - 1000)
        assertEquals(listOf("desk:5000"), calls)
        advance(1000)
        assertEquals(listOf("desk:5000", "new:5000"), calls)
    }

    @Test
    fun everyPeerIsAsked() {
        peers = listOf("a:5000", "b:5000", "c:5001")
        advance(0)
        assertEquals(peers.toSet(), calls.toSet())
        assertEquals(3, log.size)
        assertTrue(log[0], log[0].contains("Asked a:5000 to release the speakers (new session): 200"))
    }

    @Test
    fun unreachablePeerIsSkipped() {
        peers = listOf("off:5000", "on:5000")
        unreachable.add("off:5000")
        advance(0)
        assertEquals(peers.toSet(), calls.toSet())
        assertTrue(log[0], log[0].contains("off:5000 did not answer"))
    }

    @Test
    fun oncePerSession() {
        peers = listOf("desk:5000", "off:5000")
        unreachable.add("off:5000")
        advance(0)
        advance(timeout)
        advance(3_600_000)
        assertEquals(peers.toSet(), calls.toSet())
        assertEquals(2, calls.size)
        // Not after a later connection loss either.
        connected = true
        advance(60_000)
        connected = false
        advance(1000)
        advance(2 * timeout)
        assertEquals(2, calls.size)

        // A peer added on the page while searching is asked on the next check.
        peers = peers + "new:5000"
        advance(1000)
        assertEquals(3, calls.size)
        assertEquals("new:5000", calls.last())
    }

    @Test
    fun newSessionStartsAfresh() {
        advance(0)
        assertEquals(listOf("desk:5000"), calls)
        // In a new session the peers are asked again, at once: the computer
        // may have been started again since.
        takeover.newSession()
        advance(3_600_000)
        assertEquals(listOf("desk:5000", "desk:5000"), calls)
    }

    @Test
    fun quietSessionAsksNoKnownPeer() {
        takeover.quietSession()
        advance(0)
        advance(timeout * 5)
        assertEquals(emptyList<String>(), calls)
        // A peer added since is still asked once the speakers do not show up.
        peers = listOf("desk:5000", "den:2407")
        advance(timeout)
        assertEquals(listOf("den:2407"), calls)
    }

    @Test
    fun quietSessionStaysQuietAfterTheSpeakersAreLostAgain() {
        takeover.quietSession()
        connected = true
        advance(0)
        connected = false
        advance(0)
        advance(timeout * 5)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun anOpeningAfterAQuietSessionAsksAtOnce() {
        takeover.quietSession()
        advance(timeout)
        takeover.newSession()
        advance(0)
        assertEquals(listOf("desk:5000"), calls)
    }

    /** A takeover whose peers tell their phase first; "desk" let go already. */
    private fun checking(phases: Map<String, String?>, gone: Set<String> = emptySet()) = Takeover(
        peers = { peers },
        connected = { connected },
        post = { peer ->
            calls.add(peer)
            "200 {\"success\":true}" // as versions before "held" answer, also when let go already
        },
        log = { log.add(it) },
        note = { notes.add(it) },
        phase = { peer -> if (peer in gone) throw IOException("connection refused") else phases[peer] },
    )

    @Test
    fun peerThatLetGoAlreadyIsNotAsked() {
        peers = listOf("desk:5000", "den:2407")
        val t = checking(mapOf("desk:5000" to "released", "den:2407" to "connected"))
        t.check(now)
        assertEquals(listOf("den:2407"), calls)
        assertEquals("den:2407 released the speakers", notes.last())
        assertTrue(log.contains("Other devices: desk:5000 had let go of the speakers already, not asked"))
    }

    @Test
    fun aloneItHadNothingToGive() {
        peers = listOf("desk:5000")
        checking(mapOf("desk:5000" to "released")).check(now)
        assertEquals(emptyList<String>(), calls)
        assertEquals("desk:5000 did not have the speakers", notes.last())
    }

    @Test
    fun unknownPhaseStillAsks() {
        peers = listOf("desk:5000")
        checking(mapOf("desk:5000" to null)).check(now)
        assertEquals(listOf("desk:5000"), calls)
    }

    @Test
    fun unreachableIsNotAskedTwice() {
        peers = listOf("desk:5000")
        checking(emptyMap(), gone = setOf("desk:5000")).check(now)
        assertEquals(emptyList<String>(), calls)
        assertEquals("desk:5000 did not answer", notes.last())
    }

    @Test
    fun phaseOfAStatus() {
        assertEquals("released", Takeover.phaseOf("{\"connected\":false,\"note\":\"\",\"phase\":\"released\",\"problem\":\"\",\"volume\":50}"))
        assertEquals("connected", Takeover.phaseOf("{\"phase\": \"connected\"}"))
        assertEquals(null, Takeover.phaseOf("{\"success\":true}"))
        assertEquals(null, Takeover.phaseOf("404 page not found"))
    }

    @Test
    fun aRoundEndsOnTimeAlsoWhenAPeerHangs() {
        peers = listOf("desk:5000", "attic:5000")
        val hang = java.util.concurrent.CountDownLatch(1)
        val t = Takeover(
            peers = { peers },
            connected = { connected },
            post = { peer ->
                if (peer == "attic:5000") hang.await() // never answers in time
                "200 {\"success\":true}"
            },
            log = { log.add(it) },
            note = { notes.add(it) },
            phase = { "connected" },
            peerTimeoutMillis = 200,
        )
        val started = System.nanoTime()
        t.check(now)
        val tookMillis = (System.nanoTime() - started) / 1_000_000
        hang.countDown()
        assertTrue("took $tookMillis ms", tookMillis in 150..1_000)
        assertEquals("desk:5000 released the speakers", notes.last())
        assertTrue(log.contains("Other devices: attic:5000 did not answer within 200 ms"))
    }
}
