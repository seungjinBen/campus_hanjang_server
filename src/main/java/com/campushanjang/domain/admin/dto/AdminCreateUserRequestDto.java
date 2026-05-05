package com.campushanjang.domain.admin.dto;

import com.campushanjang.domain.user.entity.enums.ContactType;
import com.campushanjang.domain.user.entity.enums.Gender;
import com.campushanjang.domain.user.entity.enums.TraitKey;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record AdminCreateUserRequestDto(
        @NotBlank @Size(max = 20) String nickname,
        @NotNull Gender gender,
        @NotNull LocalDate birthDate,
        @Size(max = 100) String university,
        @NotNull ContactType contactType,
        @NotBlank String contactValue,
        @NotNull List<TraitEntry> traits,
        List<IdealEntry> ideals
) {
    public record TraitEntry(
            @NotNull TraitKey traitKey,
            String traitValue,
            boolean isVisible
    ) {}

    public record IdealEntry(
            @NotNull TraitKey traitKey,
            String traitValue
    ) {}
}
