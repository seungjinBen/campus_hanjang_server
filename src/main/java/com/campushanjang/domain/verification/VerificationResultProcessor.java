package com.campushanjang.domain.verification;

import com.campushanjang.common.exception.BusinessException;
import com.campushanjang.common.exception.ErrorCode;
import com.campushanjang.domain.user.UserRepository;
import com.campushanjang.domain.user.entity.User;
import com.campushanjang.domain.verification.agent.AgentDecision;
import com.campushanjang.domain.verification.entity.Department;
import com.campushanjang.domain.verification.entity.StudentVerification;
import com.campushanjang.domain.verification.entity.enums.VerificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 에이전트 판정 결과의 저장 전담.
 * VerificationService에서 분리한 이유: LLM 호출(최대 60초)이 트랜잭션 안에서 실행되면
 * HikariCP 커넥션을 장시간 점유하므로, 트랜잭션 경계를 저장 단계로만 최소화한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationResultProcessor {

    private final StudentVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    @Transactional
    public StudentVerification persist(UUID userId, AgentDecision decision, String imageHash) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        StudentVerification verification = StudentVerification.builder()
                .user(user)
                .status(decision.status())
                .extractedUniversity(decision.university())
                .extractedName(decision.name())
                .extractedStudentNo(decision.studentNo())
                .extractedDepartment(decision.department())
                .extractedBirthDate(decision.birthDate())
                .confidenceScore(decision.confidence())
                .decisionReason(truncate(decision.reason(), 500))
                .agentActions(decision.actionsJson())
                .imageHash(imageHash)
                .processingMs((int) decision.processingMs())
                .llmCalls(decision.llmCalls())
                .build();

        verificationRepository.save(verification);

        if (decision.status() == VerificationStatus.AUTO_APPROVED) {
            // 인증 추출 정보로 프로필 자동 채움 — 온보딩의 생년월일/대학/학과 입력 단계 제거
            user.applyStudentVerification(decision.university(), decision.department(), decision.birthDate());
            registerDepartmentIfNew(decision.department());
        }

        return verification;
    }

    // 학과 동적 사전 — 승인 확정 시에만 신규 등록 (검수 대기 건이 사전을 오염시키지 않도록)
    public void registerDepartmentIfNew(String department) {
        if (department == null || department.isBlank()) return;
        if (!departmentRepository.existsByName(department.trim())) {
            departmentRepository.save(Department.builder().name(department.trim()).build());
            log.info("신규 학과 등록 department={}", department.trim());
        }
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
