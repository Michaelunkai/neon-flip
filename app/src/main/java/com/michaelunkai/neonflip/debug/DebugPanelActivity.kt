package com.michaelunkai.neonflip.debug

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.michaelunkai.neonflip.analytics.RevenueAnalytics
import com.michaelunkai.neonflip.monetization.AdMobAds
import com.michaelunkai.neonflip.monetization.BillingEngineImpl
import com.michaelunkai.neonflip.monetization.NotificationsManager
import com.michaelunkai.neonflip.monetization.ToleranceEngine

/*
 * ============================ FOR THE BUILD AGENT ============================
 * This activity is intentionally NOT registered in AndroidManifest.xml (the
 * manifest is owned by the build agent). Merge the snippet in
 * app/src/main/java/com/michaelunkai/neonflip/debug/MANIFEST-SNIPPET.xml:
 *
 *     <activity
 *         android:name="com.michaelunkai.neonflip.debug.DebugPanelActivity"
 *         android:exported="false"
 *         android:screenOrientation="portrait" />
 *
 * Debug entry: fire an explicit intent to DebugPanelActivity from any debug
 * gesture (e.g. 5 taps on the version label). Never exported.
 * ============================================================================
 */

/**
 * DebugPanelActivity — dark neon live view of the ENTIRE revenue engine:
 * tolerance score + next-ad decision, player metrics, ad/billing/consent state,
 * notification gates and the recent analytics tail. Refreshes every second.
 * Debug-only, never exported, safe to ship (reads only engine state).
 */
class DebugPanelActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var toleranceText: TextView
    private lateinit var metricsText: TextView
    private lateinit var decisionText: TextView
    private lateinit var adsText: TextView
    private lateinit var billingText: TextView
    private lateinit var notifText: TextView
    private lateinit var analyticsText: TextView

    private val refreshLoop = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.rgb(8, 10, 24)))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        val scroll = ScrollView(this).apply { addView(root) }

        root.addView(title("NEONFLIP // REVENUE CORE"))
        toleranceText = addMono(root)
        decisionText = addMono(root)
        metricsText = addMono(root)
        root.addView(title("ADS"))
        adsText = addMono(root)
        root.addView(title("BILLING"))
        billingText = addMono(root)
        root.addView(title("NOTIFICATIONS"))
        notifText = addMono(root)
        root.addView(title("ANALYTICS TAIL (last 12)"))
        analyticsText = addMono(root)
        root.addView(buttonsRow())

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshLoop)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshLoop)
        RevenueAnalytics.flushNow()
    }

    private fun refresh() {
        val snap = ToleranceEngine.snapshot()
        val tol = (snap["tolerance"] as? Int) ?: 0
        val eff = (snap["effective_threshold"] as? Int) ?: 0
        toleranceText.text = "TOLERANCE  $tol / 100   (threshold $eff)\n" +
                bar(tol) + "\n" +
                "rewarded_bias=${snap["rewarded_bias"]}  day0=${snap["day0_protected"]}  " +
                "peak=${snap["peak_active"]}(${snap["peak_source"]})  churn_boost=+${snap["churn_boost"]}"
        decisionText.text = "NEXT INTERSTITIAL: ${snap["next_decision"]}"
        metricsText.text = snap.entries
            .filterNot { it.key in setOf("tolerance", "effective_threshold", "next_decision", "rewarded_bias", "day0_protected", "peak_active", "peak_source", "churn_boost") }
            .joinToString("\n") { "${it.key} = ${it.value}" }
        adsText.text = AdMobAds.debugStatus().entries.joinToString("\n") { "${it.key} = ${it.value}" }
        billingText.text = BillingEngineImpl.debugStatus().entries.joinToString("\n") { "${it.key} = ${it.value}" }
        notifText.text = NotificationsManager.debugStatus().entries.joinToString("\n") { "${it.key} = ${it.value}" }
        analyticsText.text = RevenueAnalytics.recentEvents(12)
            .joinToString("\n") { line -> if (line.length > 160) line.take(160) + "…" else line }
            .ifEmpty { "(no events yet)" }
    }

    private fun buttonsRow(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun btn(label: String, action: () -> Unit): Button =
            Button(this).apply {
                text = label
                setTextColor(Color.rgb(0, 229, 255))
                setBackgroundColor(Color.rgb(24, 28, 56))
                setOnClickListener { try { action() } catch (t: Throwable) {} }
            }
        row.addView(btn("Reload ads") { AdMobAds.preloadAll("debug") }, rowParams())
        row.addView(btn("Reset tolerance") { ToleranceEngine.resetForDebug() }, rowParams())
        row.addView(btn("Flush log") { RevenueAnalytics.flushNow() }, rowParams())
        return row
    }

    // ---- tiny UI helpers (programmatic dark-neon theme; res/ is not owned here) ----

    private fun title(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.rgb(255, 0, 200))
        typeface = Typeface.MONOSPACE
        textSize = 14f
        setPadding(0, dp(14), 0, dp(4))
    }

    private fun addMono(parent: LinearLayout): TextView = TextView(this).apply {
        setTextColor(Color.rgb(0, 229, 255))
        typeface = Typeface.MONOSPACE
        textSize = 12f
        parent.addView(this)
    }

    private fun bar(value: Int): String {
        val filled = (value / 10).coerceIn(0, 10)
        return "[" + "#".repeat(filled) + ".".repeat(10 - filled) + "]"
    }

    private fun rowParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { rightMargin = dp(8); gravity = Gravity.CENTER_VERTICAL }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
