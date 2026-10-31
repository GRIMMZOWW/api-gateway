package com.example.gateway.keys;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/** Generates a new API key: "gk_" + base64url(32 random bytes) without padding. */
@Component
public class KeyGenerator {

    private static final String KEY_PREFIX_LITERAL = "gk_";
    private static final int RANDOM_BYTES = 32;
    private static final Base64.Encoder URL_ENCODER_NO_PAD = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom random = new SecureRandom();
    private final KeyHasher hasher;

    public KeyGenerator(KeyHasher hasher) {
        this.hasher = hasher;
    }

    public GeneratedKey generate() {
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        String fullKey = KEY_PREFIX_LITERAL + URL_ENCODER_NO_PAD.encodeToString(bytes);
        String prefix = hasher.prefixOf(fullKey);
        String hash = hasher.hash(fullKey);
        return new GeneratedKey(fullKey, prefix, hash);
    }

    /** The freshly generated key. */
    public record GeneratedKey(String fullKey, String prefix, String hash) {}
}
