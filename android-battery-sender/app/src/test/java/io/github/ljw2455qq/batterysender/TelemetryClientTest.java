package io.github.ljw2455qq.batterysender;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class TelemetryClientTest {
    @Test
    public void appendsBatteryPathToDatabaseRoot() {
        assertEquals(
                "https://example-default-rtdb.firebaseio.com/battery.json",
                TelemetryClient.normalizeEndpoint("https://example-default-rtdb.firebaseio.com/"));
    }

    @Test
    public void keepsExplicitJsonPath() {
        assertEquals(
                "https://example-default-rtdb.firebaseio.com/custom-battery.json",
                TelemetryClient.normalizeEndpoint(
                        "https://example-default-rtdb.firebaseio.com/custom-battery.json"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInsecureEndpoint() {
        TelemetryClient.normalizeEndpoint("http://example.test");
    }
}
