package com.example.partyplanner.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.partyplanner.R;
import com.example.partyplanner.ui.EventDetailsActivity;

public class NotificationHelper {

    public static final String CHANNEL_ID = "party_reminders";
    private static final String CHANNEL_NAME = "Напоминания о событиях";

    public static void ensureChannel(Context context) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm =
                context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel existing =
                nm.getNotificationChannel(CHANNEL_ID);

        if (existing != null) return;

        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );

        ch.setDescription("Уведомления о событиях");
        ch.enableVibration(true);
        ch.enableLights(true);

        nm.createNotificationChannel(ch);
    }

    public static void showEventReminder(Context context,
                                         String eventId,
                                         String title,
                                         String address,
                                         long dateTime) {

        ensureChannel(context);

        Intent intent = new Intent(context, EventDetailsActivity.class);
        intent.putExtra("eventId", eventId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pi = PendingIntent.getActivity(
                context,
                eventId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (android.os.Build.VERSION.SDK_INT >= 23
                                ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        // 📅 Форматирование даты и времени
        java.text.DateFormat timeFormat =
                new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());

        java.text.DateFormat dateFormat =
                new java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault());

        String timeStr = timeFormat.format(new java.util.Date(dateTime));
        String dateStr = dateFormat.format(new java.util.Date(dateTime));

        String text;

        // Если событие сегодня — красивее написать “Сегодня”
        java.util.Calendar today = java.util.Calendar.getInstance();
        java.util.Calendar eventCal = java.util.Calendar.getInstance();
        eventCal.setTimeInMillis(dateTime);

        boolean isToday =
                today.get(java.util.Calendar.YEAR) == eventCal.get(java.util.Calendar.YEAR)
                        && today.get(java.util.Calendar.DAY_OF_YEAR) == eventCal.get(java.util.Calendar.DAY_OF_YEAR);

        if (isToday) {
            text = "Сегодня в " + timeStr;
        } else {
            text = dateStr + " в " + timeStr;
        }

        if (address != null && !address.trim().isEmpty()) {
            text += " • " + address;
        }

        NotificationCompat.Builder b =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(title == null ? "Событие" : title)
                        .setContentText(text)
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT);

// ✅ Линтер перестанет истерить
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        androidx.core.app.NotificationManagerCompat.from(context)
                .notify(Math.abs(eventId.hashCode()), b.build());    }
}