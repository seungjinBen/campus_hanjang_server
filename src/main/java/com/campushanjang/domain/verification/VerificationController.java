package com.campushanjang.domain.verification;

import com.campushanjang.common.response.ApiResponse;
import com.campushanjang.config.UserRateLimit;
import com.campushanjang.domain.verification.dto.VerificationStatusResponseDto;
import com.campushanjang.domain.verification.dto.VerificationSubmitResponseDto;
import com.campushanjang.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/verification")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService verificationService;

    // 학생앱 QR 화면 캡처 업로드 → 에이전트 판정 (시간당 5회 — LLM 비용 방어)
    @PostMapping("/submit")
    @UserRateLimit(operation = "verification:submit")
    public ResponseEntity<ApiResponse<VerificationSubmitResponseDto>> submit(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) {
        VerificationSubmitResponseDto result = verificationService.submit(principal.getId(), file);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/status")
    @UserRateLimit(operation = "verification:status")
    public ResponseEntity<ApiResponse<VerificationStatusResponseDto>> getStatus(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(verificationService.getStatus(principal.getId())));
    }
}
