package com.michaelunkai.neonflip.retention

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives the daily streak-save alarm, posts the (capped) notification and
 * schedules the next day's alarm. Manifest-declared, exported=false.
 */
class StreakAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            NotificationHelper.maybePostStreakSave(context)
        } catch (_: Throwable) {
            // never crash from a notification path
        } finally {
            try { NotificationHelper.scheduleStreakSave(context) } catch (_: Throwable) {}
        }
    }
}
