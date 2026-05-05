package com.campushanjang.config;

import com.campushanjang.common.exception.BusinessException;
import com.campushanjang.common.exception.ErrorCode;
import com.campushanjang.security.UserPrincipal;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class UserRateLimitAspect {

    private final RateLimitConfig rateLimitConfig;

    @Around("@annotation(userRateLimit)")
    public Object checkUserRateLimit(ProceedingJoinPoint joinPoint, UserRateLimit userRateLimit) throws Throwable {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            // 어드민은 rate limit 적용 안 함
            if ("ADMIN".equals(principal.getRole())) {
                return joinPoint.proceed();
            }
            Bucket bucket = rateLimitConfig.getUserBucket(
                    principal.getId().toString(), userRateLimit.operation());
            if (!bucket.tryConsume(1)) {
                throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
            }
        }
        return joinPoint.proceed();
    }
}
