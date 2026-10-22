package vn.giapha.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtSecretGuardTest {
    @Test
    void prodWithDefaultSecretRefusesToStart() {
        assertThatThrownBy(() -> new JwtSecretGuard(JwtSecretGuard.DEFAULT_SECRET, true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void devDefaultsAndCustomProdSecretAreFine() {
        assertThatCode(() -> new JwtSecretGuard(JwtSecretGuard.DEFAULT_SECRET, false)).doesNotThrowAnyException();
        assertThatCode(() -> new JwtSecretGuard("a-real-long-random-secret-0123456789abcdef", true)).doesNotThrowAnyException();
    }
}
