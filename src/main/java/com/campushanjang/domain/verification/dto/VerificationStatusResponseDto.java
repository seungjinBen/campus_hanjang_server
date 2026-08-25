package com.campushanjang.domain.verification.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VerificationStatusResponseDto {

    private final String status;      // NONE / AUTO_APPROVED / RETRY_REQUESTED / NEEDS_REVIEW / APPROVED / REJECTED
    private final boolean verified;   // 온보딩 진행 가능 여부
}
