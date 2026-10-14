package com.shilapi.xcertplay.orchestration

import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchEventQueueTest {
    /** Holds drain work until the test decides the worker thread runs it. */
    private class ManualExecutor : Executor {
        val tasks = ArrayDeque<Runnable>()
        var rejecting = false
        override fun execute(command: Runnable) {
            if (rejecting) throw RejectedExecutionException("worker is shut down")
            tasks.addLast(command)
        }
        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }

    private fun queue(
        worker: Executor,
        sent: MutableList<String>,
        failOn: String? = null,
    ) = TouchEventQueue<String>(worker) { item ->
        if (item == failOn) throw IOException("event write failed")
        sent.add(item)
    }

    @Test fun enqueueReturnsBeforeTheWorkerRunsAndKeepsOrder() {
        val worker = ManualExecutor()
        val sent = mutableListOf<String>()
        val queue = queue(worker, sent)
        assertTrue(queue.enqueue("down", coalescable = false))
        assertTrue(queue.enqueue("up", coalescable = false))
        assertTrue("The caller must never wait for the socket write", sent.isEmpty())
        worker.runAll()
        assertEquals(listOf("down", "up"), sent)
    }

    @Test fun consecutiveMovesCollapseToTheNewestPosition() {
        val worker = ManualExecutor()
        val sent = mutableListOf<String>()
        val queue = queue(worker, sent)
        for (move in listOf("move1", "move2", "move3")) {
            assertTrue(queue.enqueue(move, coalescable = true))
        }
        worker.runAll()
        assertEquals("A stalled write must not replay stale points", listOf("move3"), sent)
    }

    @Test fun transitionsAreNeverDroppedOrReordered() {
        val worker = ManualExecutor()
        val sent = mutableListOf<String>()
        val queue = queue(worker, sent)
        queue.enqueue("down", coalescable = false)
        queue.enqueue("move1", coalescable = true)
        queue.enqueue("move2", coalescable = true)
        queue.enqueue("up", coalescable = false)
        worker.runAll()
        assertEquals(listOf("down", "move2", "up"), sent)
    }

    @Test fun aMoveQueuedBehindATransitionIsNotMergedIntoIt() {
        val worker = ManualExecutor()
        val sent = mutableListOf<String>()
        val queue = queue(worker, sent)
        queue.enqueue("move1", coalescable = true)
        queue.enqueue("down", coalescable = false)
        worker.runAll()
        assertEquals("The press must arrive before later moves", listOf("move1", "down"), sent)
    }

    @Test fun aRejectedWorkerDropsTheBacklogAndReportsFailure() {
        val worker = ManualExecutor()
        val sent = mutableListOf<String>()
        val queue = queue(worker, sent)
        worker.rejecting = true
        assertFalse(queue.enqueue("move1", coalescable = true))
        worker.rejecting = false
        assertTrue(queue.enqueue("down", coalescable = false))
        worker.runAll()
        assertEquals("Reports queued for a dead worker must not replay later", listOf("down"), sent)
    }

    @Test fun aFailingSendDoesNotStopTheRemainingReports() {
        val worker = ManualExecutor()
        val sent = mutableListOf<String>()
        val queue = queue(worker, sent, failOn = "bad")
        queue.enqueue("bad", coalescable = false)
        queue.enqueue("good", coalescable = false)
        worker.runAll()
        assertEquals(listOf("good"), sent)
    }

    @Test fun clearDropsPendingReports() {
        val worker = ManualExecutor()
        val sent = mutableListOf<String>()
        val queue = queue(worker, sent)
        queue.enqueue("move1", coalescable = true)
        queue.enqueue("move2", coalescable = true)
        queue.clear()
        worker.runAll()
        assertTrue(sent.isEmpty())
        assertTrue(queue.enqueue("down", coalescable = false))
        worker.runAll()
        assertEquals(listOf("down"), sent)
    }
}
