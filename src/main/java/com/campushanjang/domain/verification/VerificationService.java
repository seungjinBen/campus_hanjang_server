package com.campushanjang.domain.verification;

import com.campushanjang.common.exception.BusinessException;
import com.campushanjang.common.exception.ErrorCode;
import com.campushanjang.domain.user.UserRepository;
import com.campushanjang.domain.user.entity.User;
import com.campushanjang.domain.verification.agent.AgentDecision;
import com.campushanjang.domain.verification.agent.VerificationAgent;
import com.campushanjang.domain.verification.dto.VerificationStatusResponseDto;
import com.campushanjang.domain.verification.dto.VerificationSubmitResponseDto;
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
    public VerificationSubmitResponseDto submit(UUID userId, MultipartFile file) {
        log.info("학생인증 제출 userId={} size={} contentType={}", userId, file.getSize(), file.getContentType());

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

        AgentDecision decision = verificationAgent.verify(bytes, mediaType);

        try {
            resultProcessor.persist(userId, decision, imageHash);
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
                .build();
    }

    @Transactional(readOnly = true)
    public VerificationStatusResponseDto getStatus(UUID userId) {
        return verificationRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .map(v -> VerificationStatusResponseDto.builder()
                        .status(v.getStatus().name())
                        .verified(v.getStatus() == VerificationStatus.AUTO_APPROVED
                                || v.getStatus() == VerificationStatus.APPROVED)
                        .build())
                .orElseGet(() -> VerificationStatusResponseDto.builder()
                        .status("NONE")
                        .verified(false)
                        .build());
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
