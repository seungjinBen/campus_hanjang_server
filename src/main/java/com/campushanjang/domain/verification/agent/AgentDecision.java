package com.campushanjang.domain.verification.agent;

import com.campushanjang.domain.verification.entity.enums.VerificationStatus;

import java.time.LocalDate;

// 에이전트 최종 판정 결과 + 메트릭 — 서비스 레이어가 이 값으로 저장/유저 반영/응답을 처리
public record AgentDecision(
        VerificationStatus status,
        double confidence,
        String university,
        String name,
        String studentNo,
        String department,
        LocalDate birthDate,
        String reason,        // 관리자용 판단 근거
        String userMessage,   // 사용자 노출 안내문 (재촬영 가이드, 거절 사유 등)
        int llmCalls,
        long processingMs,
        String actionsJson    // 도구 호출 이력 (JSON 배열)
) {
}
