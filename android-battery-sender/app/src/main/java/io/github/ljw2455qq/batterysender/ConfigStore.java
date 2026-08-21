package io.github.ljw2455qq.batterysender;

import android.content.Context;
import android.content.SharedPreferences;

final class ConfigStore {
    private static final String PREFS = "battery_sender";
    private static final String KEY_DATABASE_URL = "database_url";
    private static final String KEY_ENABLED = "service_enabled";
    private static final String KEY_LAST_STATUS = "last_status";

    private ConfigStore() {}

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String databaseUrl(Context context) {
        return preferences(context).getString(KEY_DATABASE_URL, "");
    }

    static void saveDatabaseUrl(Context context, String url) {
        preferences(context).edit().putString(KEY_DATABASE_URL, url.trim()).apply();
    }

    static boolean isEnabled(Context context) {
        return preferences(context).getBoolean(KEY_ENABLED, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    static String lastStatus(Context context) {
        return preferences(context).getString(KEY_LAST_STATUS, "중지됨");
    }

    static void setLastStatus(Context context, String status) {
        preferences(context).edit().putString(KEY_LAST_STATUS, status).apply();
    }
}

