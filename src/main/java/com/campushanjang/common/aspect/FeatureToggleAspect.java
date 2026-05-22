package com.campushanjang.common.aspect;

import com.campushanjang.common.annotation.RequiresMatchingEnabled;
import com.campushanjang.common.exception.BusinessException;
import com.campushanjang.common.exception.ErrorCode;
import com.campushanjang.common.feature.FeatureToggleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        // 매칭 미오픈 — ADMIN만 통과
        if (!featureToggleService.isMatchingEnabled()) {
            if (isAdmin) return;
            long daysLeft = featureToggleService.daysUntilMatchingOpen();
            log.warn("매칭 기능 조기 접근 시도 차단 daysLeft={} method={}",
                    daysLeft, joinPoint.getSignature().getName());
            throw new BusinessException(ErrorCode.MATCHING_NOT_AVAILABLE,
                    Map.of("daysUntilOpen", daysLeft));
        }

        // 매칭 서비스 종료 — ADMIN 및 allowAfterTermination=true 엔드포인트는 통과
        if (featureToggleService.isMatchingTerminated()) {
            if (isAdmin) return;
            RequiresMatchingEnabled annotation = ((MethodSignature) joinPoint.getSignature())
                    .getMethod().getAnnotation(RequiresMatchingEnabled.class);
            if (annotation.allowAfterTermination()) return;
            log.info("매칭 서비스 종료 후 접근 차단 method={}", joinPoint.getSignature().getName());
            throw new BusinessException(ErrorCode.MATCHING_TERMINATED);
        }
    }
}
