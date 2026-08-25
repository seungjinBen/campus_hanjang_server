package com.campushanjang.domain.verification.entity.enums;

public enum VerificationStatus {
    PENDING,          // 처리 중 (에이전트 실행 전/중)
    AUTO_APPROVED,    // 에이전트 자동 승인
    RETRY_REQUESTED,  // 이미지 품질 문제 — 재촬영 요청
    NEEDS_REVIEW,     // 애매 — 관리자 검수 큐
    APPROVED,         // 관리자 수동 승인
    REJECTED          // 거절 (에이전트 또는 관리자)
}
