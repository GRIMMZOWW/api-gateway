package com.example.gateway.auth;

import com.example.gateway.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void validPasswordPasses() {
        assertThatCode(() -> policy.validate("Hunter12")).doesNotThrowAnyException();
    }

    @Test
    void tooShortThrows() {
        assertThatThrownBy(() -> policy.validate("Ab1")).isInstanceOf(ApiException.class);
    }

    @Test
    void noDigitThrows() {
        assertThatThrownBy(() -> policy.validate("abcdefgh")).isInstanceOf(ApiException.class);
    }

    @Test
    void noLetterThrows() {
        assertThatThrownBy(() -> policy.validate("12345678")).isInstanceOf(ApiException.class);
    }

    @Test
    void nullThrows() {
        assertThatThrownBy(() -> policy.validate(null)).isInstanceOf(ApiException.class);
    }
}
