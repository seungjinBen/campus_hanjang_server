package com.campushanjang.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// 테스트 계정 로그인 — prod에서는 LOCAL_LOGIN_ALLOWED_EMAILS 화이트리스트에 등록된 이메일만 허용
@Getter
@NoArgsConstructor
public class LocalLoginRequestDto {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 50, message = "비밀번호는 8자 이상이어야 해요")
    private String password;

    // 초대 코드 (선택) — 리퍼럴 E2E 테스트용
    private String refCode;

    // 신규 계정 생성 시에만 사용 — 학생인증을 거치지 않으므로 직접 입력받아
    // applyStudentVerification 경로로 세팅한다 (후보 풀 자격 birthDate·매칭 게이트 충족)
    @Past(message = "생년월일이 올바르지 않아요")
    private LocalDate birthDate;

    @Size(max = 100)
    private String department;
}
