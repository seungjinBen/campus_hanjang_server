package com.campushanjang.domain.verification.dto;

import lombok.Builder;
import lombok.Getter;

// 자소서 지표 4종: 자동승인율 / 평균 처리시간 / 검수 부담 / 재시도 성공률
@Getter
@Builder
public class VerificationStatsDto {

    private final long total;
    private final long autoApproved;
    private final long manualApproved;
    private final long needsReview;       // 현재 검수 큐 크기
    private final long retryRequested;
    private final long rejected;
    private final double autoApprovalRate;
    private final Double avgProcessingMs;
    private final long totalLlmCalls;
    private final double retrySuccessRate; // 재촬영 요청받은 유저 중 최종 승인 도달 비율
}
