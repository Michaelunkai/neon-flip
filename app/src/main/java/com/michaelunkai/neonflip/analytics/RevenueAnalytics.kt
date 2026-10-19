package com.michaelunkai.neonflip.analytics

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * RevenueAnalytics — local JSON event log, Firebase-pipeable shape.
 *
 * Every event is one JSON line: {"ts":<epoch_ms>,"event":"<name>","params":{...}}.
 * Events ring-buffer in memory up to RevenueConfig.ANALYTICS_LOG_MAX_EVENTS and
 * are appended to filesDir/revenue_analytics.jsonl on a background thread
 * (coalesced, ~2s after bursts). The same {"event":..., "params":{...}} shape
 * maps 1:1 onto FirebaseAnalytics.logEvent for a later drop-in pipe.
 *
 * Canonical event names emitted by the revenue engine:
 *   session_start, session_end, death, high_score, quest_complete,
 *   ad_show, ad_click, ad_decline, ad_skip, ad_unavailable, preload,
 *   iap_attempt, iap_purchase, iap_ack, entitlement, billing_ready,
 *   tolerance_snapshot, consent, notif_sent, notif_suppressed, notif_opt_in
 *
 * Owner: analytics package. Game/retention code calls log() directly too.
 */
object RevenueAnalytics {

    private const val FILE_NAME = "revenue_analytics.jsonl"
    private const val FLUSH_DELAY_MS = 2_000L
    private const val TAIL_LOAD_LINES = 500 // cross-session continuity on cold start

    private var appContext: Context? = null
    private val buffer = ArrayDeque<JSONObject>()
    private val io = Executors.newSingleThreadExecutor()
    private val dirty = AtomicBoolean(false)
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    @Volatile private var persistedCount = 0   // lines already in the file
    @Volatile private var droppedEvents = 0L   // evicted by the ring buffer
    @Volatile private var loaded = false

    // ---------------------------------------------------------------- API (existing signature preserved)

    /** Log one structured event. Never throws, never blocks, works offline. */
    fun log(event: String, params: Map<String, Any?> = emptyMap()) {
        try {
            val safe = LinkedHashMap<String, Any?>(params.size + 1)
            safe["ts"] = System.currentTimeMillis()
            safe["event"] = event
            safe["params"] = JSONObject(params)
            synchronized(buffer) {
                buffer.addLast(JSONObject(safe))
                while (buffer.size > com.michaelunkai.neonflip.monetization.RevenueConfig.ANALYTICS_LOG_MAX_EVENTS) {
                    buffer.removeFirst()
                    droppedEvents++
                    if (persistedCount > 0) persistedCount-- // next flush rewrites (compaction)
                }
            }
            scheduleFlush()
        } catch (t: Throwable) { /* analytics must never crash the game */ }
    }

    /** Aggregate live snapshot for dashboards / debug panel. */
    fun snapshot(): Map<String, Any?> {
        val counts = LinkedHashMap<String, Int>()
        val recent: List<Map<String, Any?>>
        synchronized(buffer) {
            recent = buffer.takeLast(20).map { j ->
                mapOf("ts" to j.optLong("ts"), "event" to j.optString("event"),
                    "params" to j.optJSONObject("params")?.toString())
            }
            buffer.forEach { j ->
                val name = j.optString("event", "?")
                counts[name] = (counts[name] ?: 0) + 1
            }
        }
        return mapOf(
            "buffered_events" to counts.values.sum(),
            "capacity" to com.michaelunkai.neonflip.monetization.RevenueConfig.ANALYTICS_LOG_MAX_EVENTS,
            "dropped_events" to droppedEvents,
            "counts_by_event" to counts,
            "recent" to recent,
            "file_bytes" to fileBytes(),
            "persisted_count" to persistedCount
        )
    }

    /** Last [n] events as raw JSON strings (debug panel tail). */
    fun recentEvents(n: Int): List<String> = synchronized(buffer) {
        buffer.takeLast(n).map { it.toString() }
    }

    /** Force a synchronous disk flush (also called from Activity.onPause). */
    fun flushNow() {
        try { io.execute { flushLocked() } } catch (t: Throwable) {}
    }

    /** Debug-panel only: wipe buffer + file. */
    fun clear() {
        try {
            synchronized(buffer) { buffer.clear() }
            persistedCount = 0
            droppedEvents = 0
            io.execute {
                try { file()?.delete() } catch (t: Throwable) {}
            }
        } catch (t: Throwable) {}
    }

    // ---------------------------------------------------------------- internals

    /** Called by monetization engines' attach(); safe to call repeatedly. */
    fun init(context: Context) {
        if (loaded) return
        loaded = true
        appContext = context.applicationContext
        try {
            io.execute {
                try {
                    val f = file() ?: return@execute
                    if (!f.exists()) return@execute
                    val tail = f.readLines().takeLast(TAIL_LOAD_LINES)
                    synchronized(buffer) {
                        tail.forEach { line ->
                            try { buffer.addLast(JSONObject(line)) } catch (t: Throwable) {}
                        }
                        while (buffer.size > com.michaelunkai.neonflip.monetization.RevenueConfig.ANALYTICS_LOG_MAX_EVENTS)
                            buffer.removeFirst()
                        persistedCount = buffer.size
                    }
                } catch (t: Throwable) {}
            }
        } catch (t: Throwable) {}
    }

    private fun scheduleFlush() {
        if (!dirty.compareAndSet(false, true)) return
        try {
            handler.postDelayed({
                if (dirty.compareAndSet(true, false)) {
                    try { io.execute { flushLocked() } } catch (t: Throwable) {}
                }
            }, FLUSH_DELAY_MS)
        } catch (t: Throwable) { dirty.set(false) }
    }

    private fun flushLocked() {
        try {
            val f = file() ?: return
            val toAppend: List<JSONObject>
            synchronized(buffer) {
                if (persistedCount >= buffer.size) return
                toAppend = buffer.drop(persistedCount)
                persistedCount = buffer.size
            }
            val newLines = toAppend.joinToString("\n") { it.toString() }
            if (persistedCount >= com.michaelunkai.neonflip.monetization.RevenueConfig.ANALYTICS_LOG_MAX_EVENTS) {
                // ring compacted: rewrite whole file bounded to capacity
                val all = synchronized(buffer) { buffer.joinToString("\n") { it.toString() } }
                f.writeText(all + (if (all.isEmpty()) "" else "\n"))
            } else {
                f.appendText(newLines + "\n")
            }
        } catch (t: Throwable) {}
    }

    private fun file(): File? = try {
        appContext?.let { File(it.filesDir, FILE_NAME) }
    } catch (t: Throwable) { null }

    private fun fileBytes(): Long = try { file()?.length() ?: 0L } catch (t: Throwable) { 0L }
}
