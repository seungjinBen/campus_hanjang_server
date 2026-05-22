package com.campushanjang.domain.common;

import com.campushanjang.common.feature.FeatureToggleService;
import com.campushanjang.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/service")
@RequiredArgsConstructor
public class ServiceStatusController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final FeatureToggleService featureToggleService;

    /**
     * 서비스 오픈 현황 조회 (인증 불필요).
     * serverDate는 오직 서버 시스템 시간(Asia/Seoul)으로 계산한다.
     * 클라이언트가 어떤 헤더를 보내도 이 값은 변하지 않는다.
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<ServiceStatusResponseDto>> getStatus() {
        ServiceStatusResponseDto status = new ServiceStatusResponseDto(
                featureToggleService.isUserCollectionEnabled(),
                featureToggleService.isMatchingEnabled(),
                featureToggleService.isMatchingTerminated(),
                featureToggleService.daysUntilMatchingOpen(),
                featureToggleService.getMatchingOpenDate().toString(),
                LocalDate.now(SEOUL).toString()
        );
        return ResponseEntity.ok(ApiResponse.ok(status));
    }

    public record ServiceStatusResponseDto(
            boolean userCollectionEnabled,
            boolean matchingEnabled,
            boolean matchingTerminated,
            long daysUntilMatchingOpen,
            String matchingOpenDate,
            String serverDate
    ) {}
}
