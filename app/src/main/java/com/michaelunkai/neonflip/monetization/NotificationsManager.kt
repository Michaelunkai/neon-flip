package com.michaelunkai.neonflip.monetization

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.michaelunkai.neonflip.analytics.RevenueAnalytics
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * NotificationsManager — streak-save / streak-bonus local notifications.
 *
 * HARD GATES (all must pass, order = cheapest first):
 *   1. Opt-in: user consented (default OFF; game surfaces a settings toggle).
 *   2. Daily cap: <= RevenueConfig.NOTIF_MAX_PER_DAY per calendar day.
 *   3. Quiet hours: NOT sent inside [NOTIF_QUIET_START_HOUR, NOTIF_QUIET_END_HOUR)
 *      (window wraps midnight: 21:00 -> 09:00).
 *   4. Runtime permission on API 33+ (POST_NOTIFICATIONS).
 *
 * Zero-crash doctrine: every path is wrapped; a failed notification is logged
 * and swallowed. Game integration:
 *      NotificationsManager.init(context)
 *      NotificationsManager.setOptIn(true)                    // from settings UI
 *      NotificationsManager.notifyStreakSave(streakDays, 6)   // streak at risk
 *      NotificationsManager.notifyStreakBonus(streakDays)     // streak milestone
 */
object NotificationsManager {

    private const val PREFS_FILE = "neonflip_notifications"
    private const val KEY_OPT_IN = "opt_in"
    private const val KEY_SENT_COUNT = "sent_count"
    private const val KEY_SENT_DAY = "sent_day"
    private const val CHANNEL_ID = "neonflip_revenue_nudge"

    private var appContext: Context? = null
    private var prefs: android.content.SharedPreferences? = null
    private var optIn = false

    fun init(context: Context) {
        if (prefs != null) return
        try {
            appContext = context.applicationContext
            prefs = appContext!!.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            optIn = prefs!!.getBoolean(KEY_OPT_IN, false)
            createChannelIfNeeded()
        } catch (t: Throwable) { prefs = null }
    }

    /** Opt-in gate — game settings toggle calls this. Default false. */
    fun setOptIn(enabled: Boolean) {
        optIn = enabled
        try { prefs?.edit()?.putBoolean(KEY_OPT_IN, enabled)?.apply() } catch (t: Throwable) {}
        RevenueAnalytics.log("notif_opt_in", mapOf("enabled" to enabled))
    }

    fun isOptedIn(): Boolean = optIn

    /** Streak is about to break: "N-day streak ends tonight — play one flip to save it". */
    fun notifyStreakSave(streakDays: Int, hoursRemaining: Int): Boolean = notify(
        title = "Your $streakDays-day streak is at risk",
        body = "Play one round in the next ${hoursRemaining}h to keep it alive."
    )

    /** Streak milestone reached: positive dopamine reinforcement. */
    fun notifyStreakBonus(streakDays: Int): Boolean = notify(
        title = "$streakDays-day streak. See you tomorrow?",
        body = "Keep the run alive for a bonus coin drop."
    )

    // ---------------------------------------------------------------- core

    private fun notify(title: String, body: String): Boolean {
        val ctx = appContext
        if (ctx == null || !canSendNow()) {
            RevenueAnalytics.log("notif_suppressed", mapOf("title" to title))
            return false
        }
        return try {
            val intent = Intent().setClassName(ctx.packageName, "com.michaelunkai.neonflip.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pi = PendingIntent.getActivity(
                ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification: Notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info) // platform icon; res/ is not owned here
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(nextNotifyId(), notification)
            markSent()
            RevenueAnalytics.log("notif_sent", mapOf("title" to title))
            true
        } catch (t: Throwable) {
            RevenueAnalytics.log("notif_suppressed", mapOf("error" to (t.message ?: "unknown")))
            false
        }
    }

    /** Gate pipeline: opt-in -> daily cap -> quiet hours -> runtime permission. */
    private fun canSendNow(): Boolean {
        if (!optIn) return false
        if (dailyCount() >= RevenueConfig.NOTIF_MAX_PER_DAY) return false
        if (inQuietHours()) return false
        if (Build.VERSION.SDK_INT >= 33) {
            val ctx = appContext ?: return false
            val granted = ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return true
    }

    private fun inQuietHours(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val start = RevenueConfig.NOTIF_QUIET_START_HOUR // 21
        val end = RevenueConfig.NOTIF_QUIET_END_HOUR     // 9
        return if (start > end) hour >= start || hour < end else hour in start until end
    }

    private fun dailyCount(): Int {
        val p = prefs ?: return 0
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return if (p.getString(KEY_SENT_DAY, null) == today) p.getInt(KEY_SENT_COUNT, 0) else 0
    }

    private fun markSent() {
        val p = prefs ?: return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val count = dailyCount() + 1
        p.edit().putString(KEY_SENT_DAY, today).putInt(KEY_SENT_COUNT, count).apply()
    }

    private fun nextNotifyId(): Int = 0x4E00 + dailyCount() // stable small ids

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < 26) return
        val ctx = appContext ?: return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Streak reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    /** Live state for the debug panel. */
    fun debugStatus(): Map<String, Any?> = mapOf(
        "opt_in" to optIn,
        "sent_today" to dailyCount(),
        "cap" to RevenueConfig.NOTIF_MAX_PER_DAY,
        "quiet_hours" to inQuietHours()
    )
}
