package com.campushanjang.domain.user.dto;

import com.campushanjang.domain.user.entity.enums.ContactType;
import com.campushanjang.domain.user.entity.enums.Gender;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class UserProfileRequestDto {

    @NotBlank
    @Size(min = 1, max = 20, message = "닉네임은 1~20자 사이여야 해요")
    private String nickname;

    @NotNull
    private Gender gender;

    @NotNull
    @JsonFormat(pattern = "yyyyMMdd")
    private LocalDate birthDate;

    private String university;

    @NotNull
    private ContactType contactType;

    @NotBlank
    private String contactValue;
}
