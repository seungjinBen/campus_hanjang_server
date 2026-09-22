package com.campushanjang.domain.verification.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VerificationSubmitResponseDto {

    private final String status;      // AUTO_APPROVED / RETRY_REQUESTED / NEEDS_REVIEW / REJECTED
    private final String message;     // 에이전트가 생성한 사용자 안내문 (재촬영 가이드 등)
    // 아래 두 필드는 AUTO_APPROVED 시에만 채워짐 — "세종대학교 컴퓨터공학과 인증 완료" 표시용
    private final String university;
    private final String department;
    // true면 에브리타임 경로라 학과·생년월일이 자동 입력되지 않음 — 프론트가 보충 입력 화면으로 라우팅
    private final boolean needsSupplementaryInfo;
}
