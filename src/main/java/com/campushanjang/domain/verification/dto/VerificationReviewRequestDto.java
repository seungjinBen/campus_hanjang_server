package com.campushanjang.domain.verification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class VerificationReviewRequestDto {

    @NotBlank
    @Pattern(regexp = "APPROVE|REJECT", message = "action은 APPROVE 또는 REJECT여야 해요")
    private String action;

    // REJECT 시 관리자가 남기는 사유 — 선택 입력, 500자 이내
    @Size(max = 500, message = "거절 사유는 500자 이내로 입력해주세요")
    private String rejectionNote;
}
