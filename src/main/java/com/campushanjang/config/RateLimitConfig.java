package com.campushanjang.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class RateLimitConfig {

    // prod: 100/min, dev: application-dev.yml 에서 완화 가능
    @Value("${rate-limit.global-per-minute:100}")
    private long globalPerMinute;

    // prod: 10/hour, dev: application-dev.yml 에서 완화 가능
    @Value("${rate-limit.local-login-per-hour:10}")
    private long localLoginPerHour;

    // prod: 30/min, dev: 부하 테스트 시 완화 (500VU 대응)
    @Value("${rate-limit.user-cards-per-minute:30}")
    private long userCardsPerMinute;

    // prod: 10/min, dev: 부하 테스트 시 완화
    @Value("${rate-limit.user-received-per-minute:10}")
    private long userReceivedPerMinute;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .maximumSize(50_000)
            .build();

    public Bucket getIpBucket(String ip) {
        return buckets.get("ip:" + ip, k -> buildGlobalBucket());
    }

    public Bucket getKakaoCallbackBucket(String ip) {
        return buckets.get("kakao:" + ip, k -> buildKakaoCallbackBucket());
    }

    // 개발용 로컬 로그인 — 실서비스 전 삭제 예정
    public Bucket getLocalLoginBucket(String ip) {
        return buckets.get("local-login:" + ip, k -> buildLocalLoginBucket());
    }

    public Bucket getUserBucket(String userId, String operation) {
        return buckets.get(operation + ":" + userId, k -> buildUserBucket(operation));
    }

    public Bucket getServiceStatusBucket(String ip) {
        return buckets.get("service-status:" + ip, k -> buildServiceStatusBucket());
    }

    private Bucket buildGlobalBucket() {
        Bandwidth limit = Bandwidth.classic(globalPerMinute, Refill.greedy(globalPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    // 60분당 10회 — 카카오 콜백 IP
    private Bucket buildKakaoCallbackBucket() {
        Bandwidth limit = Bandwidth.classic(10, Refill.greedy(10, Duration.ofHours(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    // 개발용 로컬 로그인 IP
    private Bucket buildLocalLoginBucket() {
        Bandwidth limit = Bandwidth.classic(localLoginPerHour, Refill.greedy(localLoginPerHour, Duration.ofHours(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    // 엔드포인트별 유저 버킷 설정
    private Bucket buildUserBucket(String operation) {
        Bandwidth limit = switch (operation) {
            case "match:select" -> Bandwidth.classic(10, Refill.greedy(10, Duration.ofHours(1)));
            case "match:note" -> Bandwidth.classic(20, Refill.greedy(20, Duration.ofHours(1)));
            case "match:note:respond" -> Bandwidth.classic(30, Refill.greedy(30, Duration.ofHours(1)));
            case "photo:upload" -> Bandwidth.classic(10, Refill.greedy(10, Duration.ofHours(1)));
            // 분당 제한 — 자동화 스크립트로 정보 탈취 시도 차단 (dev에서는 완화 가능)
            case "match:cards:read" -> Bandwidth.classic(userCardsPerMinute, Refill.greedy(userCardsPerMinute, Duration.ofMinutes(1)));
            case "match:received" -> Bandwidth.classic(userReceivedPerMinute, Refill.greedy(userReceivedPerMinute, Duration.ofMinutes(1)));
            case "user:me:read" -> Bandwidth.classic(30, Refill.greedy(30, Duration.ofMinutes(1)));
            default -> Bandwidth.classic(30, Refill.greedy(30, Duration.ofHours(1)));
        };
        return Bucket.builder().addLimit(limit).build();
    }

    // /api/service/status — 인증 불필요 엔드포인트 IP 기준 제한
    private Bucket buildServiceStatusBucket() {
        Bandwidth limit = Bandwidth.classic(60, Refill.greedy(60, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }
}
