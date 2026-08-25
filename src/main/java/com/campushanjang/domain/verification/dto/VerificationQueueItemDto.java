package com.campushanjang.domain.verification.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// 관리자 검수 큐 항목 — 검수가 목적이므로 추출 정보 전체 포함 (ADMIN 전용 응답)
@Getter
@Builder
public class VerificationQueueItemDto {

    private final UUID verificationId;
    private final UUID userId;
    private final String nickname;
    private final String extractedUniversity;
    private final String extractedName;
    private final String extractedStudentNo;
    private final String extractedDepartment;
    private final LocalDate extractedBirthDate;
    private final Double confidenceScore;
    private final String decisionReason;   // 에이전트의 검수자용 판단 근거
    private final LocalDateTime createdAt;
}
