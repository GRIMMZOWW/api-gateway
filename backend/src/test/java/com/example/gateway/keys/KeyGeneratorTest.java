package com.example.gateway.keys;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit test of KeyGenerator. */
class KeyGeneratorTest {

    private final KeyGenerator generator = new KeyGenerator(new KeyHasher());

    @Test
    void fullKeyStartsWithGkAndIs46Chars() {
        KeyGenerator.GeneratedKey g = generator.generate();
        assertThat(g.fullKey()).startsWith("gk_");
        assertThat(g.fullKey()).hasSize(46);
    }

    @Test
    void twoCallsProduceDifferentKeys() {
        KeyGenerator.GeneratedKey a = generator.generate();
        KeyGenerator.GeneratedKey b = generator.generate();
        assertThat(a.fullKey()).isNotEqualTo(b.fullKey());
        assertThat(a.hash()).isNotEqualTo(b.hash());
    }

    @Test
    void prefixIsFirst11CharsAndStartsWithGk() {
        KeyGenerator.GeneratedKey g = generator.generate();
        assertThat(g.prefix()).hasSize(11);
        assertThat(g.prefix()).startsWith("gk_");
        assertThat(g.prefix()).isEqualTo(g.fullKey().substring(0, 11));
    }

    @Test
    void hashIs64LowercaseHexChars() {
        KeyGenerator.GeneratedKey g = generator.generate();
        assertThat(g.hash()).hasSize(64);
        assertThat(g.hash()).matches("[0-9a-f]{64}");
    }

    @Test
    void hashIsStableForSameFullKey() {
        KeyGenerator.GeneratedKey g = generator.generate();
        KeyHasher hasher = new KeyHasher();
        String recomputed = hasher.hash(g.fullKey());
        // Same full key always hashes to the same digest, and that digest
        // matches what the generator produced.
        assertThat(recomputed).isEqualTo(g.hash());
        assertThat(hasher.hash(g.fullKey())).isEqualTo(recomputed);
    }
}
