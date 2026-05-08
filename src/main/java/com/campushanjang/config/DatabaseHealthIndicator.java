package com.campushanjang.config;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("database")
@RequiredArgsConstructor
public class DatabaseHealthIndicator implements HealthIndicator {

    private final EntityManager entityManager;

    // 동시 헬스체크 폭주 방지 — SELECT 1 결과를 5초간 캐싱
    private volatile Health cachedHealth = null;
    private volatile long cacheExpiryMs = 0L;
    private static final long CACHE_TTL_MS = 5_000L;

    @Override
    public Health health() {
        long now = System.currentTimeMillis();
        if (cachedHealth != null && now < cacheExpiryMs) {
            return cachedHealth;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (cachedHealth != null && now < cacheExpiryMs) {
                return cachedHealth;
            }
            Health result;
            try {
                entityManager.createNativeQuery("SELECT 1").getSingleResult();
                result = Health.up().build();
            } catch (Exception e) {
                result = Health.down().withException(e).build();
            }
            cachedHealth = result;
            cacheExpiryMs = now + CACHE_TTL_MS;
            return result;
        }
    }
}
