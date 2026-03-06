package com.example.partyplanner.reminders;

import android.content.Context;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.partyplanner.data.EventWithDetails;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ReminderScheduler {

    private static String workName(String eventId) {
        return "reminder_event_" + eventId;
    }

    public static void cancel(Context ctx, String eventId) {
        WorkManager.getInstance(ctx).cancelUniqueWork(workName(eventId));
    }

    public static void scheduleExact(Context ctx, EventWithDetails e) {
        if (e == null || e.event == null || e.event.id == null) return;

        if (!e.event.reminderEnabled) {
            cancel(ctx, e.event.id);
            return;
        }

        long now = System.currentTimeMillis();
        long triggerAt = e.event.reminderAtMillis;

        if (triggerAt <= now) {
            cancel(ctx, e.event.id);
            return;
        }

        long delay = triggerAt - now;

        Data input = new Data.Builder()
                .putString(EventReminderWorker.KEY_EVENT_ID, e.event.id)
                .putString(EventReminderWorker.KEY_TITLE, e.event.title)
                .putString(EventReminderWorker.KEY_ADDRESS, e.event.address)
                .putLong(EventReminderWorker.KEY_DATETIME, e.event.dateTime)
                .build();

        OneTimeWorkRequest req =
                new OneTimeWorkRequest.Builder(EventReminderWorker.class)
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .setInputData(input)
                        .addTag("party_reminders")
                        .build();

        WorkManager.getInstance(ctx).enqueueUniqueWork(
                workName(e.event.id),
                ExistingWorkPolicy.REPLACE,
                req
        );
    }

    public static void scheduleExact(Context ctx, com.example.partyplanner.data.EventEntity event) {
        if (event == null || event.id == null) return;

        if (!event.reminderEnabled) {
            cancel(ctx, event.id);
            return;
        }

        long now = System.currentTimeMillis();
        long triggerAt = event.reminderAtMillis;

        if (triggerAt <= now) {
            cancel(ctx, event.id);
            return;
        }

        long delay = triggerAt - now;

        Data input = new Data.Builder()
                .putString(EventReminderWorker.KEY_EVENT_ID, event.id)
                .putString(EventReminderWorker.KEY_TITLE, event.title)
                .putString(EventReminderWorker.KEY_ADDRESS, event.address)
                .putLong(EventReminderWorker.KEY_DATETIME, event.dateTime)
                .build();

        OneTimeWorkRequest req =
                new OneTimeWorkRequest.Builder(EventReminderWorker.class)
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .setInputData(input)
                        .addTag("party_reminders")
                        .build();

        WorkManager.getInstance(ctx).enqueueUniqueWork(
                workName(event.id),
                ExistingWorkPolicy.REPLACE,
                req
        );
    }

}