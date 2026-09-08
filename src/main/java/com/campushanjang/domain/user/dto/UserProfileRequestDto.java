package com.campushanjang.domain.user.dto;

import com.campushanjang.domain.user.entity.enums.ContactType;
import com.campushanjang.domain.user.entity.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 생년월일·대학·학과는 학생인증에서 자동 입력되므로 요청으로 받지 않는다 (CLAUDE.md 16-1)
@Getter
@NoArgsConstructor
public class UserProfileRequestDto {

    @NotBlank
    @Size(min = 1, max = 20, message = "닉네임은 1~20자 사이여야 해요")
    private String nickname;

    @NotNull
    private Gender gender;

    @NotNull
    private ContactType contactType;

    @NotBlank
    @Size(max = 100, message = "연락처는 100자 이내여야 해요")
    private String contactValue;
}
