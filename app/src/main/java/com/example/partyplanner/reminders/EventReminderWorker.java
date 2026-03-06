package com.example.partyplanner.reminders;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.partyplanner.util.NotificationHelper;

public class EventReminderWorker extends Worker {

    public static final String KEY_EVENT_ID = "event_id";
    public static final String KEY_TITLE = "title";
    public static final String KEY_ADDRESS = "address";
    public static final String KEY_DATETIME = "date_time";

    public EventReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String eventId = getInputData().getString(KEY_EVENT_ID);
        String title = getInputData().getString(KEY_TITLE);
        String address = getInputData().getString(KEY_ADDRESS);
        long dateTime = getInputData().getLong(KEY_DATETIME, -1L);

        if (eventId == null || eventId.trim().isEmpty()) return Result.failure();

        NotificationHelper.showEventReminder(getApplicationContext(), eventId, title, address, dateTime);
        return Result.success();
    }
}