package com.campushanjang.domain.verification;

import com.campushanjang.common.exception.BusinessException;
import com.campushanjang.common.exception.ErrorCode;
import com.campushanjang.domain.user.UserRepository;
import com.campushanjang.domain.user.entity.User;
import com.campushanjang.domain.verification.agent.AgentDecision;
import com.campushanjang.domain.verification.agent.VerificationAgent;
import com.campushanjang.domain.verification.dto.SupplementaryInfoRequestDto;
import com.campushanjang.domain.verification.dto.VerificationStatusResponseDto;
import com.campushanjang.domain.verification.dto.VerificationSubmitResponseDto;
import com.campushanjang.domain.verification.entity.enums.VerificationMethod;
import com.campushanjang.domain.verification.entity.enums.VerificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final long MAX_SIZE = 10L * 1024 * 1024;

    private final VerificationAgent verificationAgent;
    private final VerificationResultProcessor resultProcessor;
    private final StudentVerificationRepository verificationRepository;
    private final UserRepository userRepository;

    // 의도적으로 @Transactional 없음 — LLM 호출(최대 60초)이 DB 커넥션을 점유하지 않도록
    // 조회는 개별 쿼리, 저장은 VerificationResultProcessor의 트랜잭션으로 분리
    public VerificationSubmitResponseDto submit(UUID userId, MultipartFile file, VerificationMethod method) {
        log.info("학생인증 제출 userId={} size={} contentType={} method={}", userId, file.getSize(), file.getContentType(), method);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.isStudentVerified()) {
            throw new BusinessException(ErrorCode.VERIFICATION_ALREADY_APPROVED);
        }

        byte[] bytes = readBytes(file);
        String mediaType = detectMediaType(bytes);
        String imageHash = sha256(bytes);

        // 동일 캡처 재사용 차단 — LLM 호출 전 선처리 (비용 0원 방어선)
        if (verificationRepository.existsByImageHash(imageHash)) {
            throw new BusinessException(ErrorCode.VERIFICATION_DUPLICATE_IMAGE);
        }

        AgentDecision decision = verificationAgent.verify(bytes, mediaType, method);

        try {
            resultProcessor.persist(userId, decision, imageHash, method);
        } catch (DataIntegrityViolationException e) {
            // 승인 학번 부분 유니크 인덱스 최종 방어 — 동시 요청으로 같은 학번이 먼저 승인된 경우
            log.warn("학번 유니크 충돌 userId={}", userId);
            throw new BusinessException(ErrorCode.VERIFICATION_STUDENT_NO_TAKEN);
        }

        // 메트릭 로깅 — 이름·학번·생년월일은 민감 정보라 로그에서 제외
        log.info("학생인증 판정 완료 userId={} status={} confidence={} processingMs={} llmCalls={}",
                userId, decision.status(), decision.confidence(), decision.processingMs(), decision.llmCalls());

        boolean approved = decision.status() == VerificationStatus.AUTO_APPROVED;
        return VerificationSubmitResponseDto.builder()
                .status(decision.status().name())
                .message(decision.userMessage())
                .university(approved ? decision.university() : null)
                .department(approved ? decision.department() : null)
                // 에브리타임 경로는 승인돼도 생년월일이 항상 null — 보충 입력 화면으로 안내
                .needsSupplementaryInfo(approved && decision.birthDate() == null)
                .build();
    }

    @Transactional(readOnly = true)
    public VerificationStatusResponseDto getStatus(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        return verificationRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .map(v -> buildStatus(v.getStatus().name(),
                        v.getStatus() == VerificationStatus.AUTO_APPROVED || v.getStatus() == VerificationStatus.APPROVED,
                        user))
                // student_verifications 레코드 없이 직접 인증된 계정(테스트 계정)은
                // users.is_student_verified를 최종 진실 소스로 사용
                .orElseGet(() -> buildStatus(
                        user.isStudentVerified() ? "AUTO_APPROVED" : "NONE",
                        user.isStudentVerified(),
                        user));
    }

    private VerificationStatusResponseDto buildStatus(String status, boolean verified, User user) {
        return VerificationStatusResponseDto.builder()
                .status(status)
                .verified(verified)
                .needsSupplementaryInfo(verified && user.getBirthDate() == null)
                .build();
    }

    // 에브리타임 경로 승인 후 학과·생년월일 보충 입력 — birthDate IS NULL 가드가 유일한 재호출 방지 수단 (CLAUDE.md 5)
    public void submitSupplementaryInfo(UUID userId, SupplementaryInfoRequestDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (!user.isStudentVerified()) {
            throw new BusinessException(ErrorCode.STUDENT_VERIFICATION_REQUIRED);
        }
        if (user.getBirthDate() != null) {
            throw new BusinessException(ErrorCode.VERIFICATION_SUPPLEMENTARY_ALREADY_SUBMITTED);
        }
        resultProcessor.applySupplementaryInfo(user, dto.getDepartment(), dto.getBirthDate());
        log.info("보충 정보 제출 완료 userId={}", userId);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.error("인증 이미지 읽기 실패 error={}", e.getMessage());
            throw new BusinessException(ErrorCode.PHOTO_UPLOAD_FAILED);
        }
    }

    // 스크린샷은 PNG(iOS/Android 기본)·JPEG·WEBP만 허용 — Content-Type 대신 magic bytes 검증
    private String detectMediaType(byte[] bytes) {
        if (bytes.length > MAX_SIZE) {
            throw new BusinessException(ErrorCode.PHOTO_TOO_LARGE);
        }
        if (bytes.length < 12) {
            throw new BusinessException(ErrorCode.PHOTO_INVALID_FORMAT);
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
                && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
            return "image/webp";
        }
        throw new BusinessException(ErrorCode.PHOTO_INVALID_FORMAT);
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원", e);
        }
    }
}
