package com.campushanjang.domain.verification.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

// 관리자 거절 내역 화면용 — 거절 사유 확인이 목적 (ADMIN 전용 응답)
@Getter
@Builder
public class RejectedVerificationDto {

    private final UUID verificationId;
    private final UUID userId;
    private final String nickname;
    private final String verificationMethod;
    private final String extractedUniversity;
    private final String extractedStudentNo;
    private final String extractedName;
    private final Double confidenceScore;

    // AI 에이전트가 판단한 근거 — 자동거절·수동거절 공통
    private final String decisionReason;

    // 관리자가 수동 거절 시 직접 입력한 사유 — AI 자동거절이면 null
    private final String adminRejectionNote;

    // ADMIN: 관리자 수동거절 / AI: 에이전트 자동거절
    private final String rejectedBy;

    private final LocalDateTime rejectedAt;
    private final LocalDateTime createdAt;
}
