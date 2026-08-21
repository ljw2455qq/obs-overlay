package io.github.ljw2455qq.batterysender;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BatteryTelemetryService extends Service {
    static final String ACTION_START = "io.github.ljw2455qq.batterysender.START";
    static final String ACTION_STOP = "io.github.ljw2455qq.batterysender.STOP";
    static final String ACTION_STATUS = "io.github.ljw2455qq.batterysender.STATUS";
    private static final String CHANNEL_ID = "battery_telemetry";
    private static final int NOTIFICATION_ID = 2455;
    private static final long HEARTBEAT_SECONDS = 30;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean sendQueued = new AtomicBoolean(false);
    private BroadcastReceiver batteryReceiver;
    private ConnectivityManager.NetworkCallback networkCallback;
    private PowerManager.WakeLock wakeLock;
    private volatile int level = -1;
    private volatile boolean charging;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, notification("시작 중…"));
        acquireWakeLock();
        registerBatteryReceiver();
        registerNetworkCallback();
        executor.scheduleWithFixedDelay(this::sendCurrent, 0, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            ConfigStore.setEnabled(this, false);
            executor.execute(() -> {
                sendSnapshot(false);
                stopSelf();
            });
            return START_NOT_STICKY;
        }
        ConfigStore.setEnabled(this, true);
        queueImmediateSend();
        return START_STICKY;
    }

    private void registerBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                level = rawLevel < 0 || scale <= 0 ? -1 : Math.round(rawLevel * 100f / scale);
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;
                updateNotification(level < 0 ? "배터리 확인 중…" : level + "% · 전송 대기");
                queueImmediateSend();
            }
        };
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void registerNetworkCallback() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (manager == null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                queueImmediateSend();
            }
        };
        try {
            manager.registerDefaultNetworkCallback(networkCallback);
        } catch (RuntimeException ignored) {
            networkCallback = null;
        }
    }

    private void queueImmediateSend() {
        if (!sendQueued.compareAndSet(false, true)) return;
        executor.execute(() -> {
            try {
                sendCurrent();
            } finally {
                sendQueued.set(false);
            }
        });
    }

    private void sendCurrent() {
        if (level < 0) return;
        sendSnapshot(true);
    }

    private void sendSnapshot(boolean connected) {
        String url = ConfigStore.databaseUrl(this);
        if (url.isEmpty()) {
            publishStatus("Firebase 주소가 설정되지 않았습니다.");
            return;
        }
        TelemetryClient.Result result = TelemetryClient.send(
                url, SecretStore.loadToken(this), Math.max(level, 0), charging, connected);
        String time = new SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(new Date());
        String status = result.success
                ? (connected ? time + " · " + level + "% 전송됨" : time + " · 전송 중지됨")
                : time + " · 실패: " + result.message;
        publishStatus(status);
    }

    private void publishStatus(String status) {
        ConfigStore.setLastStatus(this, status);
        updateNotification(status);
        sendBroadcast(new Intent(ACTION_STATUS).setPackage(getPackageName()).putExtra("status", status));
    }

    private void acquireWakeLock() {
        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
        if (manager == null) return;
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":Telemetry");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
    }

    private void createNotificationChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "배터리 오버레이 송신", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("서브폰 배터리 잔량을 방송 오버레이로 전송합니다.");
        manager.createNotificationChannel(channel);
    }

    private Notification notification(String content) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(
                this, 1, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(this, BatteryTelemetryService.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(
                this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setContentTitle("배터리 오버레이 전송 중")
                .setContentText(content)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, "중지", stop).build())
                .build();
    }

    private void updateNotification(String content) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, notification(content));
    }

    @Override
    public void onDestroy() {
        if (batteryReceiver != null) unregisterReceiver(batteryReceiver);
        if (networkCallback != null) {
            ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (manager != null) {
                try { manager.unregisterNetworkCallback(networkCallback); } catch (RuntimeException ignored) {}
            }
        }
        executor.shutdownNow();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
