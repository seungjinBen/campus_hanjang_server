package com.campushanjang.domain.verification;

import com.campushanjang.common.response.ApiResponse;
import com.campushanjang.config.UserRateLimit;
import com.campushanjang.domain.verification.dto.SupplementaryInfoRequestDto;
import com.campushanjang.domain.verification.dto.VerificationStatusResponseDto;
import com.campushanjang.domain.verification.dto.VerificationSubmitResponseDto;
import com.campushanjang.domain.verification.entity.enums.VerificationMethod;
import com.campushanjang.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/verification")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService verificationService;

    // 학생앱 QR(세종대) 또는 프로필(에브리타임) 화면 캡처 업로드 → 에이전트 판정 (시간당 5회 — LLM 비용 방어)
    @PostMapping("/submit")
    @UserRateLimit(operation = "verification:submit")
    public ResponseEntity<ApiResponse<VerificationSubmitResponseDto>> submit(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "method", defaultValue = "SEJONG_QR") String method
    ) {
        VerificationSubmitResponseDto result = verificationService.submit(principal.getId(), file, parseMethod(method));
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/status")
    @UserRateLimit(operation = "verification:status")
    public ResponseEntity<ApiResponse<VerificationStatusResponseDto>> getStatus(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(verificationService.getStatus(principal.getId())));
    }

    // 에브리타임 경로 승인 후 학과·생년월일 보충 입력 (LLM 호출 없음)
    @PostMapping("/supplementary-info")
    @UserRateLimit(operation = "verification:supplementary-info")
    public ResponseEntity<ApiResponse<Void>> submitSupplementaryInfo(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SupplementaryInfoRequestDto dto
    ) {
        verificationService.submitSupplementaryInfo(principal.getId(), dto);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // 알 수 없는 값이 오면 기존 경로로 안전 폴백 (프론트 배포 과도기 호환)
    private VerificationMethod parseMethod(String raw) {
        try {
            return VerificationMethod.valueOf(raw);
        } catch (Exception e) {
            log.warn("알 수 없는 인증 방법 — SEJONG_QR로 폴백 raw={}", raw);
            return VerificationMethod.SEJONG_QR;
        }
    }
}
