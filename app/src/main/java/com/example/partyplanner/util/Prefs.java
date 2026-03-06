package com.example.partyplanner.util;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {

    private static final String FILE = "party_prefs";
    private static final String KEY_USER_ID = "current_user_id";

    private static final String KEY_REMINDERS_ENABLED = "reminders_enabled";
    private static final String KEY_REMINDER_MINUTES = "reminder_minutes";

    public static void setCurrentUserId(Context context, String id) {
        SharedPreferences sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_USER_ID, id).apply();
    }

    public static String getCurrentUserId(Context context) {
        SharedPreferences sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        return sp.getString(KEY_USER_ID, "p1");
    }

    public static void setRemindersEnabled(Context context, boolean enabled) {
        SharedPreferences sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        sp.edit().putBoolean(KEY_REMINDERS_ENABLED, enabled).apply();
    }

    public static boolean isRemindersEnabled(Context context) {
        SharedPreferences sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_REMINDERS_ENABLED, true);
    }

    // за сколько минут напоминать (по умолчанию 30)
    public static void setReminderMinutes(Context context, int minutes) {
        SharedPreferences sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        sp.edit().putInt(KEY_REMINDER_MINUTES, minutes).apply();
    }

    public static int getReminderMinutes(Context context) {
        SharedPreferences sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        return sp.getInt(KEY_REMINDER_MINUTES, 30);
    }
}