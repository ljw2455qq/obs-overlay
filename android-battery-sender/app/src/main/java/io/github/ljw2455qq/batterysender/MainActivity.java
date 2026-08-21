package io.github.ljw2455qq.batterysender;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private EditText urlInput;
    private EditText tokenInput;
    private TextView statusText;
    private boolean receiverRegistered;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            renderStatus(intent.getStringExtra("status"));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        requestNotificationPermission();
    }

    private View buildUi() {
        int padding = dp(20);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);

        TextView title = text("오버레이 배터리 송신기", 24, Color.BLACK);
        title.setPadding(0, 0, 0, dp(12));
        content.addView(title);

        TextView intro = text(
                "삼성 인터넷을 열지 않아도 서브폰 배터리를 30초마다 전송합니다. " +
                        "전송 중에는 알림이 계속 표시됩니다.", 16, Color.DKGRAY);
        intro.setPadding(0, 0, 0, dp(20));
        content.addView(intro);

        content.addView(text("Firebase Realtime Database 주소", 14, Color.DKGRAY));
        urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setHint("https://YOUR_PROJECT-default-rtdb.firebaseio.com");
        urlInput.setText(ConfigStore.databaseUrl(this));
        content.addView(urlInput, matchWrap());

        content.addView(text("쓰기 인증 토큰 (사용할 때만)", 14, Color.DKGRAY));
        tokenInput = new EditText(this);
        tokenInput.setSingleLine(true);
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenInput.setHint("비어 있으면 인증 없이 전송");
        tokenInput.setText(SecretStore.loadToken(this));
        content.addView(tokenInput, matchWrap());

        Button start = button("저장하고 전송 시작");
        start.setOnClickListener(v -> startSending());
        content.addView(start, matchWrapWithTop(dp(18)));

        Button stop = button("전송 중지");
        stop.setOnClickListener(v -> stopSending());
        content.addView(stop, matchWrapWithTop(dp(8)));

        Button batterySettings = button("배터리 최적화 설정 열기");
        batterySettings.setOnClickListener(v -> openBatterySettings());
        content.addView(batterySettings, matchWrapWithTop(dp(8)));

        statusText = text("", 16, Color.rgb(20, 90, 40));
        statusText.setPadding(0, dp(24), 0, dp(12));
        content.addView(statusText);

        TextView help = text(
                "삼성 설정 → 배터리 → 백그라운드 사용 제한 → 절전 예외 앱에 이 앱을 추가하세요. " +
                        "방송이 끝나면 앱 또는 알림의 ‘중지’를 누르세요.", 14, Color.DKGRAY);
        content.addView(help);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        return scroll;
    }

    private void startSending() {
        String url = urlInput.getText().toString().trim();
        try {
            TelemetryClient.normalizeEndpoint(url);
        } catch (Exception error) {
            urlInput.setError(error.getMessage());
            return;
        }
        try {
            ConfigStore.saveDatabaseUrl(this, url);
            SecretStore.saveToken(this, tokenInput.getText().toString());
        } catch (Exception error) {
            Toast.makeText(this, "인증 토큰 저장 실패", Toast.LENGTH_LONG).show();
            return;
        }
        ConfigStore.setEnabled(this, true);
        Intent service = new Intent(this, BatteryTelemetryService.class)
                .setAction(BatteryTelemetryService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);
        renderStatus("전송 서비스를 시작했습니다.");
    }

    private void stopSending() {
        ConfigStore.setEnabled(this, false);
        Intent service = new Intent(this, BatteryTelemetryService.class)
                .setAction(BatteryTelemetryService.ACTION_STOP);
        startService(service);
        renderStatus("전송을 중지하는 중입니다.");
    }

    private void openBatterySettings() {
        try {
            Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(details);
        } catch (RuntimeException ignored) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    @Override
    @SuppressLint("UnspecifiedRegisterReceiverFlag") // API 32 이하에는 exported 플래그 오버로드가 없음
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(BatteryTelemetryService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(statusReceiver, filter);
        receiverRegistered = true;
        renderStatus(ConfigStore.lastStatus(this));
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(statusReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    private void renderStatus(String status) {
        statusText.setText((ConfigStore.isEnabled(this) ? "● 실행 중\n" : "○ 중지됨\n")
                + (status == null ? "" : status));
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.15f);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapWithTop(int topMargin) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = topMargin;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
