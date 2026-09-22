package com.campushanjang.domain.verification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// 에브리타임 인증 경로 전용 — 화면에 없던 학과·생년월일을 승인 후 1회 보충 입력받는다
@Getter
@NoArgsConstructor
public class SupplementaryInfoRequestDto {

    @NotBlank
    @Size(max = 100)
    private String department;

    @NotNull
    @Past(message = "생년월일이 올바르지 않아요")
    private LocalDate birthDate;
}
