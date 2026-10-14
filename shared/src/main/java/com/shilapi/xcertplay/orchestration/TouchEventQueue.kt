package com.shilapi.xcertplay.orchestration

import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * Serializes reports for the AirPlay event channel on a single worker while coalescing
 * consecutive coalescable reports (touch MOVE) to the newest one.
 *
 * A stalled event-channel write can block the worker for hundreds of milliseconds on this
 * head unit. Without coalescing, a swipe queues every intermediate point behind the stall
 * and the iPhone replays the whole backlog late — the swipe stops feeling direct. Reports
 * that must not be lost (touch DOWN/UP) keep their order; only runs of consecutive
 * coalescable reports collapse into their newest member.
 */
internal class TouchEventQueue<T>(
    private val worker: Executor,
    private val send: (T) -> Unit,
) {
    private val lock = Any()
    private val pending = ArrayDeque<Pair<Boolean, T>>()
    private var draining = false

    /** Queues [item]; returns false when the worker rejected the queue (it is then dropped). */
    fun enqueue(item: T, coalescable: Boolean): Boolean {
        synchronized(lock) {
            if (coalescable && pending.lastOrNull()?.first == true) pending.removeLast()
            pending.addLast(coalescable to item)
            if (draining) return true
            draining = true
        }
        return try {
            worker.execute(::drain)
            true
        } catch (_: RejectedExecutionException) {
            synchronized(lock) {
                draining = false
                pending.clear()
            }
            false
        }
    }

    /** Drops every report that has not been sent yet; the in-flight one still completes. */
    fun clear() {
        synchronized(lock) { pending.clear() }
    }

    private fun drain() {
        while (true) {
            val next = synchronized(lock) {
                val item = pending.removeFirstOrNull()
                if (item == null) draining = false
                item
            } ?: return
            try {
                send(next.second)
            } catch (_: Exception) {
                // A failed write tears the session down by itself; keep draining the rest
                // so the queue never sticks in the draining state.
            }
        }
    }
}
