package com.campushanjang.domain.referral;

import com.campushanjang.common.response.ApiResponse;
import com.campushanjang.config.UserRateLimit;
import com.campushanjang.domain.referral.dto.ReferralMeResponseDto;
import com.campushanjang.domain.referral.dto.ReferralPreviewResponseDto;
import com.campushanjang.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/referral")
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralService referralService;

    // 내 초대 코드 조회 (없으면 생성) + 오늘 보너스 상태
    @GetMapping("/me")
    @UserRateLimit(operation = "referral:me")
    public ResponseEntity<ApiResponse<ReferralMeResponseDto>> getMyReferral(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(referralService.getMyReferral(principal.getId())));
    }

    // 초대 링크 OG 미리보기용 — permitAll (SecurityConfig), 글로벌 IP rate limit 적용
    @GetMapping("/preview")
    public ResponseEntity<ApiResponse<ReferralPreviewResponseDto>> preview(@RequestParam String code) {
        return ResponseEntity.ok(ApiResponse.ok(referralService.preview(code)));
    }
}
