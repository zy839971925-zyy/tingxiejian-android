package com.example.tingxiejian;

/** Durable replacement policy; Android Keystore and preferences are injected at its boundary. */
final class SecretMigration {
    interface Storage {
        String legacy();
        String encrypted();
        /** Atomically set/remove the envelope AND remove the legacy plaintext. */
        boolean replace(String envelope);
    }
    interface Crypto {
        String encrypt(String value) throws Exception;
        String decrypt(String envelope) throws Exception;
    }
    static final class Result {
        final String value;
        final boolean failed;
        Result(String value, boolean failed) { this.value = value; this.failed = failed; }
    }

    static Result read(Storage storage, Crypto crypto) {
        String envelope = storage.encrypted();
        String legacy = storage.legacy();
        if (envelope != null && !envelope.isEmpty()) {
            try {
                String value = crypto.decrypt(envelope);
                // Leftover plaintext is discarded only after the encrypted value is readable.
                boolean cleaned = legacy == null || storage.replace(envelope);
                return new Result(value, !cleaned);
            } catch (Exception unavailable) {
                // A stale legacy value must never resurrect a different, replaced key.
                return new Result("", true);
            }
        }
        if (legacy == null || legacy.isEmpty()) return new Result("", false);
        return new Result(legacy, !replaceVerified(storage, crypto, legacy));
    }

    static boolean save(Storage storage, Crypto crypto, String value) {
        String key = value == null ? "" : value;
        if (key.isEmpty()) return storage.replace(null);
        String envelope = storage.encrypted();
        if (envelope != null && !envelope.isEmpty()) {
            try {
                if (key.equals(crypto.decrypt(envelope))) {
                    return storage.legacy() == null || storage.replace(envelope);
                }
            } catch (Exception unavailable) { /* An explicit replacement can recover an invalid key. */ }
        }
        return replaceVerified(storage, crypto, key);
    }

    private static boolean replaceVerified(Storage storage, Crypto crypto, String value) {
        try {
            String envelope = crypto.encrypt(value);
            // Never delete the only usable key unless the new ciphertext was authenticated.
            if (!value.equals(crypto.decrypt(envelope))) return false;
            return storage.replace(envelope);
        } catch (Exception unavailable) {
            return false;
        }
    }
}
