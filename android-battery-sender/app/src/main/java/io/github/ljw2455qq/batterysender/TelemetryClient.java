package io.github.ljw2455qq.batterysender;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class TelemetryClient {
    static final class Result {
        final boolean success;
        final String message;

        Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    static String normalizeEndpoint(String configuredUrl) {
        String value = configuredUrl == null ? "" : configuredUrl.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (!value.endsWith(".json")) value += "/battery.json";
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("Firebase HTTPS 주소를 입력하세요.");
        }
        return value;
    }

    static Result send(String configuredUrl, String authToken, int level, boolean charging, boolean connected) {
        HttpURLConnection connection = null;
        try {
            String endpoint = normalizeEndpoint(configuredUrl);
            if (authToken != null && !authToken.isEmpty()) {
                endpoint += "?auth=" + URLEncoder.encode(authToken, StandardCharsets.UTF_8.name());
            }

            JSONObject payload = new JSONObject();
            payload.put("source", "android-foreground-service");
            payload.put("level", level);
            payload.put("charging", charging);
            payload.put("connected", connected);
            payload.put("timestamp", new JSONObject().put(".sv", "timestamp"));
            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);

            connection = (HttpURLConnection) new java.net.URL(endpoint).openConnection();
            connection.setRequestMethod("PUT");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }

            int status = connection.getResponseCode();
            if (status >= 200 && status < 300) return new Result(true, "전송 성공");
            return new Result(false, "Firebase HTTP " + status + ": " + readError(connection.getErrorStream()));
        } catch (Exception error) {
            return new Result(false, error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readError(InputStream input) {
        if (input == null) return "응답 없음";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            return line == null ? "응답 없음" : line.substring(0, Math.min(line.length(), 160));
        } catch (Exception ignored) {
            return "응답 읽기 실패";
        }
    }
}

