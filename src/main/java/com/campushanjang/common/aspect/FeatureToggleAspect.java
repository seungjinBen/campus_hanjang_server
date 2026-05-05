package com.campushanjang.common.aspect;

import com.campushanjang.common.exception.BusinessException;
import com.campushanjang.common.exception.ErrorCode;
import com.campushanjang.common.feature.FeatureToggleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class FeatureToggleAspect {

    private final FeatureToggleService featureToggleService;

    @Before("@annotation(com.campushanjang.common.annotation.RequiresMatchingEnabled)")
    public void checkMatchingEnabled(JoinPoint joinPoint) {
        if (featureToggleService.isMatchingEnabled()) {
            return;
        }

        // ADMIN은 오픈 전 테스트를 위해 통과
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return;
        }

        long daysLeft = featureToggleService.daysUntilMatchingOpen();
        log.warn("매칭 기능 조기 접근 시도 차단 daysLeft={} method={}",
                daysLeft, joinPoint.getSignature().getName());
        throw new BusinessException(ErrorCode.MATCHING_NOT_AVAILABLE,
                Map.of("daysUntilOpen", daysLeft));
    }
}
