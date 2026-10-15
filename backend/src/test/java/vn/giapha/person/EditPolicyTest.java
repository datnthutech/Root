package vn.giapha.person;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import vn.giapha.auth.AuthUser;
import vn.giapha.auth.Role;
import vn.giapha.common.ApiException;

class EditPolicyTest {
    static final Instant T0 = Instant.parse("2026-10-14T10:00:00Z");

    EditPolicy at(Instant now) { return new EditPolicy(Clock.fixed(now, ZoneOffset.UTC)); }

    Person made(Long createdBy, Instant createdAt) {
        Person p = new Person();
        p.setCreatedBy(createdBy);
        p.setCreatedAt(createdAt);
        return p;
    }

    @Test void adminAlwaysCan() {
        assertThat(at(T0.plusSeconds(999999)).canEdit(new AuthUser(1, Role.ADMIN), made(2L, T0))).isTrue();
    }

    @Test void viewerNeverCan() {
        var p = at(T0);
        assertThat(p.canEdit(new AuthUser(5, Role.VIEWER), made(5L, T0))).isFalse();
        assertThatThrownBy(() -> p.assertCanEdit(new AuthUser(5, Role.VIEWER), made(5L, T0)))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("FORBIDDEN"));
    }

    @Test void editorOwnerWithin24h() {
        var pol = at(T0.plusSeconds(3600));
        assertThat(pol.canEdit(new AuthUser(7, Role.EDITOR), made(7L, T0))).isTrue();
        assertThatCode(() -> pol.assertCanEdit(new AuthUser(7, Role.EDITOR), made(7L, T0))).doesNotThrowAnyException();
    }

    @Test void editorOwnerExactly24hStillAllowed() {
        assertThat(at(T0.plusSeconds(24 * 3600)).canEdit(new AuthUser(7, Role.EDITOR), made(7L, T0))).isTrue();
    }

    @Test void editorOwnerAfter24hExpired() {
        var pol = at(T0.plusSeconds(24 * 3600 + 1));
        assertThat(pol.canEdit(new AuthUser(7, Role.EDITOR), made(7L, T0))).isFalse();
        assertThatThrownBy(() -> pol.assertCanEdit(new AuthUser(7, Role.EDITOR), made(7L, T0)))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("EDIT_WINDOW_EXPIRED"));
    }

    @Test void editorNotOwnerForbidden() {
        var pol = at(T0);
        assertThat(pol.canEdit(new AuthUser(7, Role.EDITOR), made(8L, T0))).isFalse();
        assertThatThrownBy(() -> pol.assertCanEdit(new AuthUser(7, Role.EDITOR), made(8L, T0)))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("FORBIDDEN"));
    }

    @Test void personWithoutCreatorIsNotOwned() {
        assertThat(at(T0).canEdit(new AuthUser(7, Role.EDITOR), made(null, T0))).isFalse();
    }
}
