package io.github.ljw2455qq.batterysender;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecretStore {
    private static final String KEY_ALIAS = "battery_overlay_firebase_auth";
    private static final String PREF_KEY = "firebase_auth_encrypted";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    private SecretStore() {}

    static void saveToken(Context context, String token) throws Exception {
        if (token == null || token.isEmpty()) {
            ConfigStore.preferences(context).edit().remove(PREF_KEY).apply();
            return;
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();
        ByteBuffer packed = ByteBuffer.allocate(4 + iv.length + encrypted.length);
        packed.putInt(iv.length).put(iv).put(encrypted);
        ConfigStore.preferences(context).edit()
                .putString(PREF_KEY, Base64.encodeToString(packed.array(), Base64.NO_WRAP))
                .apply();
    }

    static String loadToken(Context context) {
        String packedText = ConfigStore.preferences(context).getString(PREF_KEY, "");
        if (packedText == null || packedText.isEmpty()) return "";
        try {
            ByteBuffer packed = ByteBuffer.wrap(Base64.decode(packedText, Base64.NO_WRAP));
            int ivLength = packed.getInt();
            if (ivLength < 12 || ivLength > 32 || packed.remaining() <= ivLength) return "";
            byte[] iv = new byte[ivLength];
            packed.get(iv);
            byte[] encrypted = new byte[packed.remaining()];
            packed.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }
}

