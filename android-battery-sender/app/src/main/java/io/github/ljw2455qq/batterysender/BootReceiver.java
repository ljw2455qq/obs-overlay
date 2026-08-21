package io.github.ljw2455qq.batterysender;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ConfigStore.isEnabled(context) || ConfigStore.databaseUrl(context).isEmpty()) return;
        Intent service = new Intent(context, BatteryTelemetryService.class)
                .setAction(BatteryTelemetryService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service);
        else context.startService(service);
    }
}
