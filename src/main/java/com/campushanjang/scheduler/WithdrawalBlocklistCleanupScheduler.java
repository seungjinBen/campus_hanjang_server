package com.campushanjang.scheduler;

import com.campushanjang.domain.auth.WithdrawalBlocklistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawalBlocklistCleanupScheduler {

    private final WithdrawalBlocklistRepository withdrawalBlocklistRepository;

    // 재가입 차단(24h) 목적을 다한 해시는 파기 — 개인정보 최소 보존 원칙
    @Scheduled(cron = "0 30 0 * * *")
    @Transactional
    public void purgeExpired() {
        try {
            long deleted = withdrawalBlocklistRepository
                    .deleteByReregistrationAllowedAtBefore(LocalDateTime.now());
            log.info("탈퇴 블록리스트 만료 정리 deleted={}", deleted);
        } catch (Exception e) {
            // 스케줄러 예외는 상위로 전파하지 않는다 (CLAUDE.md §10)
            log.error("탈퇴 블록리스트 정리 실패 error={}", e.getMessage(), e);
        }
    }
}
