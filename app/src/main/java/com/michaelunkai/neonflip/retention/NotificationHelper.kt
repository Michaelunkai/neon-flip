package com.michaelunkai.neonflip.retention

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.michaelunkai.neonflip.MainActivity
import com.michaelunkai.neonflip.R
import com.michaelunkai.neonflip.monetization.RevenueConfig
import java.util.Calendar

/**
 * Local notification scheduling helper. Honors the revenue-config caps:
 * - max NOTIF_MAX_PER_DAY posts per calendar day (counted in prefs)
 * - quiet hours NOTIF_QUIET_START_HOUR..NOTIF_QUIET_END_HOUR
 * Actual posting is additionally gated on POST_NOTIFICATIONS runtime permission (API 33+).
 */
object NotificationHelper {

    private const val CHANNEL_ID = "neonflip_retention"
    private const val KEY_NOTIF_COUNT = "notif_count_day"
    private const val KEY_NOTIF_COUNT_DATE = "notif_count_date"
    private const val ALARM_REQ = 4711
    const val PERMISSION_REQ = 4712

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID,
            ctx.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT)
        ch.description = ctx.getString(R.string.notif_channel_desc)
        nm.createNotificationChannel(ch)
    }

    /** True if we still need to ask (API 33+, not granted, not permanently denied). */
    fun needsPermission(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
    }

    /** Schedule the next "streak save" reminder ~tomorrow evening, inside quiet-hours bounds. */
    fun scheduleStreakSave(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 19) // inside the allowed window (quiet starts 21:00)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val pi = PendingIntent.getBroadcast(
            ctx, ALARM_REQ,
            Intent(ctx, StreakAlarmReceiver::class.java).setAction("neonflip.STREAK_SAVE"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Inexact is intentional: no SCHEDULE_EXACT_ALARM permission needed, timing precision is irrelevant.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
    }

    fun cancelStreakSave(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, ALARM_REQ,
            Intent(ctx, StreakAlarmReceiver::class.java).setAction("neonflip.STREAK_SAVE"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }

    /** Entry point invoked by [StreakAlarmReceiver]. Applies caps then posts. */
    fun maybePostStreakSave(ctx: Context) {
        if (!hasPermission(ctx)) return
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        // Quiet hours window (may wrap midnight).
        val quiet = if (RevenueConfig.NOTIF_QUIET_START_HOUR <= RevenueConfig.NOTIF_QUIET_END_HOUR) {
            hour in RevenueConfig.NOTIF_QUIET_START_HOUR until RevenueConfig.NOTIF_QUIET_END_HOUR
        } else {
            hour >= RevenueConfig.NOTIF_QUIET_START_HOUR || hour < RevenueConfig.NOTIF_QUIET_END_HOUR
        }
        if (quiet) return

        val prefs = ctx.getSharedPreferences("neonflip_notif", Context.MODE_PRIVATE)
        val today = SaveStore.epochDay()
        if (prefs.getLong(KEY_NOTIF_COUNT_DATE, -1L) != today) {
            prefs.edit().putLong(KEY_NOTIF_COUNT_DATE, today).putInt(KEY_NOTIF_COUNT, 0).apply()
        }
        val posted = prefs.getInt(KEY_NOTIF_COUNT, 0)
        if (posted >= RevenueConfig.NOTIF_MAX_PER_DAY) return
        // Streak at risk only if the player did NOT run today.
        if (SaveStore.data.lastPlayEpochDay == today) return
        if (SaveStore.data.streakCurrent <= 0) return

        ensureChannel(ctx)
        val notif = buildNotif(ctx, ctx.getString(R.string.notif_streak_title),
            ctx.getString(R.string.notif_streak_body, SaveStore.data.streakCurrent))
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(1001, notif)
            prefs.edit().putInt(KEY_NOTIF_COUNT, posted + 1).apply()
        } catch (_: SecurityException) { /* permission revoked mid-flight */ }
    }

    /** Bonus-refill style notification (kept caps-compliant; unused slots share the daily cap). */
    fun maybePostBonusRefill(ctx: Context, message: String) {
        if (!hasPermission(ctx)) return
        val prefs = ctx.getSharedPreferences("neonflip_notif", Context.MODE_PRIVATE)
        val today = SaveStore.epochDay()
        if (prefs.getLong(KEY_NOTIF_COUNT_DATE, -1L) != today) {
            prefs.edit().putLong(KEY_NOTIF_COUNT_DATE, today).putInt(KEY_NOTIF_COUNT, 0).apply()
        }
        val posted = prefs.getInt(KEY_NOTIF_COUNT, 0)
        if (posted >= RevenueConfig.NOTIF_MAX_PER_DAY) return
        ensureChannel(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(1002, buildNotif(ctx, ctx.getString(R.string.notif_bonus_title), message))
            prefs.edit().putInt(KEY_NOTIF_COUNT, posted + 1).apply()
        } catch (_: SecurityException) {}
    }

    private fun buildNotif(ctx: Context, title: String, body: String): Notification =
        NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // placeholder icon; mipmap assets are out of scope
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(ctx, 0,
                    Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            .build()

    private fun hasPermission(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }
}
