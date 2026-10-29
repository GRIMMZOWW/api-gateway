package com.example.gateway.keys;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates a new API key: {@code "gk_" + base64url(32 random bytes)} without
 * padding.
 *
 * <p>The full key is 46 characters: 3 for {@code "gk_"} and 43 for the unpadded
 * base64url encoding of 32 bytes (256 bits of entropy). The first 11 characters
 * ({@code "gk_" + 8}) become the stored display {@code key_prefix}; the SHA-256
 * hex of the full key becomes the stored {@code key_hash}.
 *
 * <p>The full key is returned exactly once, in the create response, and is
 * never logged or persisted. Delegates prefix/hash derivation to
 * {@link KeyHasher} to keep all hashing logic in one place.
 */
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

    /**
     * The freshly generated key. Hold {@code fullKey} only long enough to return
     * it from the create endpoint; never log it.
     */
    public record GeneratedKey(String fullKey, String prefix, String hash) {}
}
