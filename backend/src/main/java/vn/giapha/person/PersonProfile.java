package vn.giapha.person;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;

/** NON_NULL: trường bị ẩn (vd birthDate với VIEWER) không xuất hiện trong JSON. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PersonProfile(long id, String fullName, String nickname, Gender gender,
    LocalDate birthDate, DatePrecision birthPrecision, Integer birthYear, String birthLunar,
    LocalDate deathDate, DatePrecision deathPrecision, Integer deathYear, String deathAnniversaryLunar,
    boolean alive, String originPlace, String homePlace, String burialPlace,
    Integer generation, Integer birthOrder, String orderLabel, boolean orderNeedsConfirm,
    String photoUrl, long version, Long createdBy, Instant createdAt, boolean canEdit) {}
