package com.campushanjang.domain.verification;

import com.campushanjang.common.exception.BusinessException;
import com.campushanjang.common.exception.ErrorCode;
import com.campushanjang.domain.user.entity.User;
import com.campushanjang.domain.verification.dto.VerificationQueueItemDto;
import com.campushanjang.domain.verification.dto.VerificationStatsDto;
import com.campushanjang.domain.verification.entity.StudentVerification;
import com.campushanjang.domain.verification.entity.enums.VerificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationAdminService {

    private final StudentVerificationRepository verificationRepository;
    private final VerificationResultProcessor resultProcessor;

    @Transactional(readOnly = true)
    public List<VerificationQueueItemDto> getReviewQueue() {
        return verificationRepository.findQueueByStatus(VerificationStatus.NEEDS_REVIEW).stream()
                .map(v -> VerificationQueueItemDto.builder()
                        .verificationId(v.getId())
                        .userId(v.getUser().getId())
                        .nickname(v.getUser().getNickname())
                        .extractedUniversity(v.getExtractedUniversity())
                        .extractedName(v.getExtractedName())
                        .extractedStudentNo(v.getExtractedStudentNo())
                        .extractedDepartment(v.getExtractedDepartment())
                        .extractedBirthDate(v.getExtractedBirthDate())
                        .confidenceScore(v.getConfidenceScore())
                        .decisionReason(v.getDecisionReason())
                        .createdAt(v.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void review(UUID adminId, UUID verificationId, String action) {
        StudentVerification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND));

        if (verification.getStatus() != VerificationStatus.NEEDS_REVIEW) {
            throw new BusinessException(ErrorCode.VERIFICATION_INVALID_STATE);
        }

        if ("APPROVE".equals(action)) {
            verification.approveByReviewer(adminId);
            // 자동 승인과 동일 경로 — 유저 반영 + 학과 사전 + MAJOR 특징 자동 생성
            resultProcessor.applyApproval(
                    verification.getUser(),
                    verification.getExtractedUniversity(),
                    verification.getExtractedDepartment(),
                    verification.getExtractedBirthDate());

            // 승인 학번 부분 유니크 인덱스를 커밋 전에 검증 — 충돌 시 표준 에러로 변환
            try {
                verificationRepository.flush();
            } catch (DataIntegrityViolationException e) {
                throw new BusinessException(ErrorCode.VERIFICATION_STUDENT_NO_TAKEN);
            }
            log.info("검수 승인 verificationId={} adminId={}", verificationId, adminId);
        } else {
            verification.rejectByReviewer(adminId);
            log.info("검수 거절 verificationId={} adminId={}", verificationId, adminId);
        }
    }

    @Transactional(readOnly = true)
    public VerificationStatsDto getStats() {
        long total = verificationRepository.count();
        long autoApproved = verificationRepository.countByStatus(VerificationStatus.AUTO_APPROVED);
        long manualApproved = verificationRepository.countByStatus(VerificationStatus.APPROVED);
        long needsReview = verificationRepository.countByStatus(VerificationStatus.NEEDS_REVIEW);
        long retryRequested = verificationRepository.countByStatus(VerificationStatus.RETRY_REQUESTED);
        long rejected = verificationRepository.countByStatus(VerificationStatus.REJECTED);

        long retryUsers = verificationRepository.countDistinctUsersByStatus(VerificationStatus.RETRY_REQUESTED);
        long retryThenApproved = verificationRepository.countRetryThenApprovedUsers(
                VerificationStatus.RETRY_REQUESTED,
                Set.of(VerificationStatus.AUTO_APPROVED, VerificationStatus.APPROVED));

        return VerificationStatsDto.builder()
                .total(total)
                .autoApproved(autoApproved)
                .manualApproved(manualApproved)
                .needsReview(needsReview)
                .retryRequested(retryRequested)
                .rejected(rejected)
                .autoApprovalRate(total > 0 ? (double) autoApproved / total : 0.0)
                .avgProcessingMs(verificationRepository.findAvgProcessingMs())
                .totalLlmCalls(verificationRepository.sumLlmCalls())
                .retrySuccessRate(retryUsers > 0 ? (double) retryThenApproved / retryUsers : 0.0)
                .build();
    }
}
