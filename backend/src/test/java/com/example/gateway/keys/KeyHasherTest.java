package com.example.gateway.keys;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit test of {@link KeyHasher}. Confirms the hash is the canonical
 * SHA-256 hex of the input (verified against the well-known digest of "abc")
 * and that {@code prefixOf} returns the first 11 characters.
 */
class KeyHasherTest {

    private final KeyHasher hasher = new KeyHasher();

    @Test
    void hashOfAbcMatchesKnownSha256Hex() {
        // SHA-256("abc") — the standard test vector.
        assertThat(hasher.hash("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void hashIs64LowercaseHex() {
        assertThat(hasher.hash("any input value"))
            .hasSize(64)
            .matches("[0-9a-f]{64}");
    }

    @Test
    void prefixOfReturnsFirst11Characters() {
        // 23 chars; prefixOf must return the leading 11 ("gk_" + 8).
        String fullKey = "gk_ab12cd34remainingbytes";
        assertThat(hasher.prefixOf(fullKey))
            .hasSize(11)
            .isEqualTo("gk_ab12cd34");
    }
}
