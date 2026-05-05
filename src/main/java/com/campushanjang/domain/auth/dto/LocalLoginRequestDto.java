package com.campushanjang.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 개발용 로컬 로그인 — 실서비스 전 삭제 예정
@Getter
@NoArgsConstructor
public class LocalLoginRequestDto {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 4, max = 50)
    private String password;
}
