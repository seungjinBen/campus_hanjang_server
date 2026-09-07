package com.campushanjang.domain.verification;

import com.campushanjang.common.exception.BusinessException;
import com.campushanjang.common.exception.ErrorCode;
import com.campushanjang.common.feature.FeatureToggleService;
import com.campushanjang.domain.referral.ReferralEventRepository;
import com.campushanjang.domain.referral.entity.enums.ReferralStatus;
import com.campushanjang.domain.user.UserRepository;
import com.campushanjang.domain.user.UserTraitRepository;
import com.campushanjang.domain.user.entity.User;
import com.campushanjang.domain.user.entity.UserTrait;
import com.campushanjang.domain.user.entity.enums.TraitKey;
import com.campushanjang.domain.verification.agent.AgentDecision;
import com.campushanjang.domain.verification.entity.Department;
import com.campushanjang.domain.verification.entity.StudentVerification;
import com.campushanjang.domain.verification.entity.enums.VerificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final UserTraitRepository userTraitRepository;
    private final FeatureToggleService featureToggleService;
    private final ReferralEventRepository referralEventRepository;

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
            applyApproval(user, decision.university(), decision.department(), decision.birthDate());
        }

        return verification;
    }

    // 자동/수동 승인 공용 — 유저 반영 + 학과 사전 등록 + MAJOR 특징 자동 생성 + 얼리버드/리퍼럴 처리
    @Transactional
    public void applyApproval(User user, String university, String department, LocalDate birthDate) {
        // 인증 추출 정보로 프로필 자동 채움 — 온보딩의 생년월일/대학/학과 입력 단계 제거
        user.applyStudentVerification(university, department, birthDate);
        registerDepartmentIfNew(department);
        upsertMajorTrait(user, department);

        // 얼리버드: 사전등록 기간 내 인증 승인 완료 → 운영기간 내내 하루 선택 +1 (CLAUDE.md 16-5)
        if (featureToggleService.isEarlyBirdPeriod()) {
            user.markEarlyBird();
            log.info("얼리버드 확정 userId={}", user.getId());
        }

        // 리퍼럴 보상 확정: 초대받은 유저가 인증 승인에 도달 → 초대자에게 당일 +1
        // 가입 시점(PENDING)이 아니라 승인 시점에 확정 — 가입만 반복하는 어뷰징 차단
        referralEventRepository.findByRefereeIdAndStatus(user.getId(), ReferralStatus.PENDING)
                .ifPresent(event -> {
                    event.reward();
                    log.info("리퍼럴 보상 확정 referrerId={} refereeId={}",
                            event.getReferrer().getId(), user.getId());
                });
    }

    // 카드 표시·매칭 점수용 학과(UserTrait MAJOR)도 인증 값으로 고정 — 유저 임의 입력 방지
    private void upsertMajorTrait(User user, String department) {
        if (department == null || department.isBlank()) return;
        String dept = department.trim();
        userTraitRepository.findByUserIdAndTraitKey(user.getId(), TraitKey.MAJOR)
                .ifPresentOrElse(
                        trait -> trait.update(dept, trait.isVisible()),
                        () -> userTraitRepository.save(UserTrait.builder()
                                .user(user)
                                .traitKey(TraitKey.MAJOR)
                                .traitValue(dept)
                                .isVisible(true)
                                .build()));
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
