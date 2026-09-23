package com.campushanjang.domain.verification;

import com.campushanjang.common.response.ApiResponse;
import com.campushanjang.domain.verification.dto.RejectedVerificationDto;
import com.campushanjang.domain.verification.dto.VerificationQueueItemDto;
import com.campushanjang.domain.verification.dto.VerificationReviewRequestDto;
import com.campushanjang.domain.verification.dto.VerificationStatsDto;
import com.campushanjang.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// /api/admin/** 은 SecurityConfig에서 ADMIN 롤 필수
@RestController
@RequestMapping("/api/admin/verification")
@RequiredArgsConstructor
public class VerificationAdminController {

    private final VerificationAdminService verificationAdminService;

    @GetMapping("/queue")
    public ResponseEntity<ApiResponse<List<VerificationQueueItemDto>>> getQueue() {
        return ResponseEntity.ok(ApiResponse.ok(verificationAdminService.getReviewQueue()));
    }

    @GetMapping("/rejected")
    public ResponseEntity<ApiResponse<List<RejectedVerificationDto>>> getRejected() {
        return ResponseEntity.ok(ApiResponse.ok(verificationAdminService.getRejectedList()));
    }

    @PostMapping("/{verificationId}/review")
    public ResponseEntity<ApiResponse<Void>> review(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID verificationId,
            @Valid @RequestBody VerificationReviewRequestDto request
    ) {
        verificationAdminService.review(principal.getId(), verificationId, request.getAction(), request.getRejectionNote());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<VerificationStatsDto>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(verificationAdminService.getStats()));
    }
}
