package com.example.tingxiejian;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Real AES-GCM bytes; only the unavailable Android persistence/Keystore boundary is faked. */
public final class SecretMigrationCheck {
    private static int cases;
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        cases++;
    }
    private static final class Memory implements SecretMigration.Storage {
        String legacy, encrypted;
        boolean failCommit;
        int writes;
        public String legacy() { return legacy; }
        public String encrypted() { return encrypted; }
        public boolean replace(String envelope) {
            writes++;
            if (failCommit) return false;
            encrypted = envelope;
            legacy = null;
            return true;
        }
    }
    private static final class Gcm implements SecretMigration.Crypto {
        final SecretKey key;
        boolean failEncrypt, failDecrypt;
        Gcm() throws Exception { KeyGenerator gen = KeyGenerator.getInstance("AES"); gen.init(256); key = gen.generateKey(); }
        public String encrypt(String value) throws Exception {
            if (failEncrypt) throw new Exception("unavailable");
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key);
            c.updateAAD("cloud-api-key-v1".getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(c.getIV()) + "." + Base64.getEncoder().encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        }
        public String decrypt(String value) throws Exception {
            if (failDecrypt) throw new Exception("unavailable");
            String[] bits = value.split("\\."); Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.getDecoder().decode(bits[0])));
            c.updateAAD("cloud-api-key-v1".getBytes(StandardCharsets.UTF_8));
            return new String(c.doFinal(Base64.getDecoder().decode(bits[1])), StandardCharsets.UTF_8);
        }
    }
    public static void main(String[] args) throws Exception {
        Memory m = new Memory(); Gcm g = new Gcm(); m.legacy = "old-secret";
        SecretMigration.Result r = SecretMigration.read(m, g);
        check("old-secret".equals(r.value) && !r.failed, "migration yields key");
        check(m.legacy == null && m.encrypted != null && !m.encrypted.contains("old-secret"), "migration removes plaintext only after encryption");
        check("old-secret".equals(SecretMigration.read(m, g).value), "encrypted round trip");
        int writes = m.writes;
        check(SecretMigration.save(m, g, "old-secret") && m.writes == writes, "unchanged saved key does not rewrite");
        m = new Memory(); m.legacy = "keep-me"; g.failEncrypt = true;
        r = SecretMigration.read(m, g);
        check(r.failed && "keep-me".equals(r.value) && "keep-me".equals(m.legacy) && m.encrypted == null, "Keystore failure preserves legacy");
        check(!SecretMigration.save(m, g, "new-key") && "keep-me".equals(m.legacy), "failed new save preserves prior key");
        g.failEncrypt = false; m.failCommit = true;
        check(SecretMigration.read(m, g).failed && "keep-me".equals(m.legacy), "failed durable commit preserves legacy");
        m.failCommit = false; g.failDecrypt = true;
        check(SecretMigration.read(m, g).failed && m.encrypted == null && "keep-me".equals(m.legacy), "failed decrypt verification keeps plaintext");
        g.failDecrypt = false; SecretMigration.read(m, g);
        m.legacy = "stale-old-key"; m.encrypted = m.encrypted.substring(0, m.encrypted.length() - 4) + "AAAA";
        r = SecretMigration.read(m, g);
        check(r.failed && r.value.isEmpty() && "stale-old-key".equals(m.legacy), "tampered ciphertext fails closed without stale fallback");
        check(SecretMigration.save(m, g, "") && m.legacy == null && m.encrypted == null, "clear removes ciphertext and legacy even when unreadable");
        m = new Memory(); m.failCommit = true; m.legacy = "keep-me";
        check(!SecretMigration.save(m, g, "") && "keep-me".equals(m.legacy), "failed clear reported, no false success");
        check(SecretMigration.read(new Memory(), g).value.isEmpty(), "unconfigured read skips encryption");
        System.out.println("secret migration: " + cases + " checks passed");
    }
}
