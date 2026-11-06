// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
package keystone.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import keystone.engine.Insights
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Daily digest: due tasks, reminders (birthdays, warranties, car service, renewals...) and habits left. */
object Reminders {
    const val CHANNEL = "daily"
    private const val WORK = "keystone-daily"

    fun createChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Daily digest", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Tasks due, birthdays, warranties, car service and habits"
        })
    }

    fun schedule(ctx: Context, hour: Int) {
        val now = LocalDateTime.now()
        var next = now.withHour(hour).withMinute(0).withSecond(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val req = PeriodicWorkRequestBuilder<DigestWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun cancel(ctx: Context) { WorkManager.getInstance(ctx).cancelUniqueWork(WORK) }

    fun digest(): Pair<String, String>? {
        val e = Ks.engine
        val tasks = Insights.todayTasks(e)
        val up = e.upcoming(days = 1, overdueDays = 0)
        val habits = Insights.activeHabits(e).filterNot { e.habitDone(it.id, e.today()) }
        if (tasks.isEmpty() && up.isEmpty() && habits.isEmpty()) return null
        val lines = mutableListOf<String>()
        if (tasks.isNotEmpty()) lines += "${tasks.size} task(s): " + tasks.take(3).joinToString(", ") { it.title }
        up.take(4).forEach { u ->
            val col = e.collection(u.entry.collection)?.name ?: ""
            lines += "${u.field.name}: ${u.entry.title} ($col) " + if (u.days == 0L) "today" else "tomorrow"
        }
        if (habits.isNotEmpty()) lines += "Habits: " + habits.joinToString(", ") { it.title }
        return "Your day in Keystone" to lines.joinToString("\n")
    }

    fun post(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val (title, body) = digest() ?: return
        val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(ctx).notify(1, n) } catch (_: SecurityException) {}
    }
}

class DigestWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result { Reminders.post(applicationContext); return Result.success() }
}
