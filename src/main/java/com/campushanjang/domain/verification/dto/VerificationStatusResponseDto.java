package com.campushanjang.domain.verification.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VerificationStatusResponseDto {

    private final String status;      // NONE / AUTO_APPROVED / RETRY_REQUESTED / NEEDS_REVIEW / APPROVED / REJECTED
    private final boolean verified;   // 온보딩 진행 가능 여부
    // true면 승인은 됐지만 학과·생년월일이 비어 있음(에브리타임 경로) — 프론트가 보충 입력 화면으로 라우팅
    private final boolean needsSupplementaryInfo;
}
