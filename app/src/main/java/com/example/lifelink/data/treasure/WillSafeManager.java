package com.example.lifelink.data.treasure;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;

public class WillSafeManager {
    private static final String PREFS_NAME = "will_safe_prefs";
    private static final String KEY_ALIAS = "lifelink_will_safe_key";
    private static final String KEY_PASSWORD_SALT = "password_salt";
    private static final String KEY_PASSWORD_HASH = "password_hash";
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int HASH_BITS = 256;

    private final SharedPreferences prefs;
    private final TreasureDbHelper dbHelper;

    public WillSafeManager(Context context) {
        Context appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        dbHelper = new TreasureDbHelper(appContext);
    }

    public boolean hasPassword() {
        return prefs.contains(KEY_PASSWORD_SALT) && prefs.contains(KEY_PASSWORD_HASH);
    }

    public boolean hasWill() {
        return dbHelper.getEncryptedWill() != null;
    }

    public void setPassword(String password) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = hashPassword(password, salt);

        prefs.edit()
                .putString(KEY_PASSWORD_SALT, encode(salt))
                .putString(KEY_PASSWORD_HASH, encode(hash))
                .apply();
    }

    public boolean verifyPassword(String password) throws Exception {
        String saltValue = prefs.getString(KEY_PASSWORD_SALT, null);
        String hashValue = prefs.getString(KEY_PASSWORD_HASH, null);
        if (saltValue == null || hashValue == null) return false;

        byte[] expected = decode(hashValue);
        byte[] actual = hashPassword(password, decode(saltValue));
        return constantTimeEquals(expected, actual);
    }

    public void saveWill(String content) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey());
        byte[] encrypted = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));

        dbHelper.saveEncryptedWill(encode(cipher.getIV()), encode(encrypted));
    }

    public String loadWill() throws Exception {
        TreasureDbHelper.WillSafeEntry entry = dbHelper.getEncryptedWill();
        if (entry == null) return "";

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), new GCMParameterSpec(128, decode(entry.iv)));
        byte[] plain = cipher.doFinal(decode(entry.encryptedContent));
        return new String(plain, StandardCharsets.UTF_8);
    }

    private SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(false);
        }

        keyGenerator.init(builder.build());
        return keyGenerator.generateKey();
    }

    private byte[] hashPassword(String password, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return factory.generateSecret(spec).getEncoded();
    }

    private boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    private String encode(byte[] value) {
        return Base64.encodeToString(value, Base64.NO_WRAP);
    }

    private byte[] decode(String value) {
        return Base64.decode(value, Base64.NO_WRAP);
    }
}
