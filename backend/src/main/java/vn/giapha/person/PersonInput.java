package vn.giapha.person;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PersonInput(
    @NotBlank @Size(max = 200) String fullName,
    @Size(max = 100) String nickname,
    @NotNull Gender gender,
    LocalDate birthDate, DatePrecision birthPrecision, @Size(max = 40) String birthLunar,
    LocalDate deathDate, DatePrecision deathPrecision, @Size(max = 40) String deathAnniversaryLunar,
    Boolean alive,
    @Size(max = 200) String originPlace, @Size(max = 200) String homePlace, @Size(max = 200) String burialPlace,
    @Size(max = 20000) String biography,
    Long version) {}
