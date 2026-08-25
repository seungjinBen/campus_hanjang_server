package com.campushanjang.domain.verification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public class VerificationReviewRequestDto {

    @NotBlank
    @Pattern(regexp = "APPROVE|REJECT", message = "action은 APPROVE 또는 REJECT여야 해요")
    private String action;
}
