package com.example.tingxiejian;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** The only persistent API-key representation is an AES-GCM authenticated ciphertext. */
final class SecureSecretStore {
    private static final String ALIAS = "com.example.tingxiejian.cloud.api.v1";
    private static final String ENCRYPTED = "apiKey.aesgcm.v1";
    private static final byte[] AAD = "com.example.tingxiejian/cloud/apiKey/v1".getBytes(StandardCharsets.UTF_8);
    private static volatile boolean failed;

    private SecureSecretStore() { }

    static synchronized String read(Context context) {
        try {
            SecretMigration.Result result = SecretMigration.read(storage(context), new KeystoreCrypto());
            failed = result.failed;
            return result.value;
        } catch (RuntimeException unavailable) {
            failed = true;
            return "";
        }
    }

    static synchronized boolean save(Context context, String value) {
        try {
            boolean saved = SecretMigration.save(storage(context), new KeystoreCrypto(), value);
            failed = !saved;
            return saved;
        } catch (RuntimeException unavailable) {
            failed = true;
            return false;
        }
    }

    static String status() {
        return failed ? "密钥加密存储不可用；原有配置已保留，请解锁设备后重试或重新保存 API Key。" : "";
    }

    private static SecretMigration.Storage storage(Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(Cloud.PREFS, Context.MODE_PRIVATE);
        return new SecretMigration.Storage() {
            public String legacy() { return prefs.getString("apiKey", null); }
            public String encrypted() { return prefs.getString(ENCRYPTED, null); }
            public boolean replace(String envelope) {
                SharedPreferences.Editor edit = prefs.edit().remove("apiKey");
                if (envelope == null) edit.remove(ENCRYPTED); else edit.putString(ENCRYPTED, envelope);
                // One preferences transaction keeps migration failure from losing the legacy key.
                String oldLegacy = prefs.getString("apiKey", null);
                String oldEnvelope = prefs.getString(ENCRYPTED, null);
                try {
                    if (edit.commit()) return true;
                } catch (RuntimeException unavailable) { /* Restore the in-memory snapshot below. */ }
                // Android updates the in-memory map even when disk commit fails. Restore it,
                // so a failed migration/clear never drops the currently available credential.
                SharedPreferences.Editor rollback = prefs.edit();
                if (oldLegacy == null) rollback.remove("apiKey"); else rollback.putString("apiKey", oldLegacy);
                if (oldEnvelope == null) rollback.remove(ENCRYPTED); else rollback.putString(ENCRYPTED, oldEnvelope);
                try { rollback.commit(); } catch (RuntimeException unavailable) { /* Failure remains visible. */ }
                return false;
            }
        };
    }

    private static final class KeystoreCrypto implements SecretMigration.Crypto {
        private SecretKey key(boolean create) throws Exception {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
            if (!create) throw new java.security.KeyStoreException("missing key");
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .setKeySize(256)
                    .build());
            return generator.generateKey();
        }

        public String encrypt(String value) throws Exception {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(true));
            cipher.updateAAD(AAD);
            return "v1:" + Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":"
                    + Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        }

        public String decrypt(String envelope) throws Exception {
            String[] parts = envelope.split(":", -1);
            if (parts.length != 3 || !"v1".equals(parts[0])) throw new java.security.GeneralSecurityException("invalid envelope");
            byte[] iv = Base64.decode(parts[1], Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(parts[2], Base64.NO_WRAP);
            if (iv.length != 12 || encrypted.length < 16) throw new java.security.GeneralSecurityException("invalid envelope");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        }
    }
}
