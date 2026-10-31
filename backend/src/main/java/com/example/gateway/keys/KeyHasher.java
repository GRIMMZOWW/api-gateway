package com.example.gateway.keys;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Hashes a full API key to its SHA-256 hex digest and derives its display prefix. */
@Component
public class KeyHasher {

    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final int PREFIX_LENGTH = 11;

    public String hash(String fullKey) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(fullKey.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JCA spec; every JDK ships it.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public String prefixOf(String fullKey) {
        return fullKey.substring(0, PREFIX_LENGTH);
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}
