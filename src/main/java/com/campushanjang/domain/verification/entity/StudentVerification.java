package com.campushanjang.domain.verification.entity;

import com.campushanjang.domain.user.entity.User;
import com.campushanjang.domain.verification.entity.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "student_verifications")
@Getter
@NoArgsConstructor
public class StudentVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VerificationStatus status;

    @Column(name = "extracted_university", length = 100)
    private String extractedUniversity;

    // 실명은 인증 기록에만 저장 — users 테이블/매칭 서비스에 노출하지 않음 (닉네임 원칙 유지)
    @Column(name = "extracted_name", length = 50)
    private String extractedName;

    @Column(name = "extracted_student_no", length = 20)
    private String extractedStudentNo;

    @Column(name = "extracted_department", length = 100)
    private String extractedDepartment;

    @Column(name = "extracted_birth_date")
    private LocalDate extractedBirthDate;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    // 에이전트가 수행한 도구 호출 이력(JSON 배열) — 판단 근거 감사 추적용
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "agent_actions", columnDefinition = "jsonb")
    private String agentActions;

    @Column(name = "image_hash", nullable = false, length = 64)
    private String imageHash;

    @Column(name = "processing_ms")
    private Integer processingMs;

    @Column(name = "llm_calls")
    private Integer llmCalls;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public StudentVerification(User user, VerificationStatus status,
                               String extractedUniversity, String extractedName,
                               String extractedStudentNo, String extractedDepartment,
                               LocalDate extractedBirthDate,
                               Double confidenceScore, String decisionReason,
                               String agentActions, String imageHash,
                               Integer processingMs, Integer llmCalls) {
        this.user = user;
        this.status = status;
        this.extractedUniversity = extractedUniversity;
        this.extractedName = extractedName;
        this.extractedStudentNo = extractedStudentNo;
        this.extractedDepartment = extractedDepartment;
        this.extractedBirthDate = extractedBirthDate;
        this.confidenceScore = confidenceScore;
        this.decisionReason = decisionReason;
        this.agentActions = agentActions;
        this.imageHash = imageHash;
        this.processingMs = processingMs;
        this.llmCalls = llmCalls;
        this.createdAt = LocalDateTime.now();
    }

    // 검수자 승인 — NEEDS_REVIEW 상태에서만 호출되어야 함 (서비스에서 상태 검증)
    public void approveByReviewer(UUID reviewerId) {
        this.status = VerificationStatus.APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = LocalDateTime.now();
    }

    public void rejectByReviewer(UUID reviewerId) {
        this.status = VerificationStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = LocalDateTime.now();
    }
}
