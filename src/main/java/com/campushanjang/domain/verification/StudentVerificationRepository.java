package com.campushanjang.domain.verification;

import com.campushanjang.domain.verification.entity.StudentVerification;
import com.campushanjang.domain.verification.entity.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentVerificationRepository extends JpaRepository<StudentVerification, UUID> {

    // 유저의 최신 인증 시도 — 상태 조회 API용
    Optional<StudentVerification> findTopByUserIdOrderByCreatedAtDesc(UUID userId);

    // 동일 캡처 재사용 차단 — LLM 호출 전 선처리 (비용 0원 방어선)
    boolean existsByImageHash(String imageHash);

    // 에이전트 도구: 이미 승인된 학번인지 조회 (1학번 1계정)
    boolean existsByExtractedStudentNoAndStatusIn(String extractedStudentNo, Collection<VerificationStatus> statuses);

    // 관리자 검수 큐 — 오래된 것부터
    @Query("SELECT sv FROM StudentVerification sv JOIN FETCH sv.user WHERE sv.status = :status ORDER BY sv.createdAt ASC")
    List<StudentVerification> findQueueByStatus(@Param("status") VerificationStatus status);

    // 거절 내역 — 최신순, 관리자 거절 사유 확인용
    @Query("SELECT sv FROM StudentVerification sv JOIN FETCH sv.user WHERE sv.status = :status ORDER BY sv.createdAt DESC")
    List<StudentVerification> findRejectedByStatus(@Param("status") VerificationStatus status);

    // ── 메트릭 (자소서 지표: 자동승인율 / 평균 처리시간 / LLM 비용) ──

    long countByStatus(VerificationStatus status);

    @Query("SELECT AVG(sv.processingMs) FROM StudentVerification sv WHERE sv.processingMs IS NOT NULL")
    Double findAvgProcessingMs();

    @Query("SELECT COALESCE(SUM(sv.llmCalls), 0) FROM StudentVerification sv")
    long sumLlmCalls();

    // 재시도 성공률: RETRY_REQUESTED를 받았던 유저 중 이후 승인에 도달한 비율 산출용
    @Query("SELECT COUNT(DISTINCT sv.user.id) FROM StudentVerification sv WHERE sv.status = :status")
    long countDistinctUsersByStatus(@Param("status") VerificationStatus status);

    @Query("""
            SELECT COUNT(DISTINCT sv.user.id) FROM StudentVerification sv
            WHERE sv.status IN :approvedStatuses
            AND sv.user.id IN (
                SELECT r.user.id FROM StudentVerification r WHERE r.status = :retryStatus
            )
            """)
    long countRetryThenApprovedUsers(@Param("retryStatus") VerificationStatus retryStatus,
                                     @Param("approvedStatuses") Collection<VerificationStatus> approvedStatuses);
}
