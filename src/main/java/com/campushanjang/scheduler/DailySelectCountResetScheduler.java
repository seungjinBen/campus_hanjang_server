package com.campushanjang.scheduler;

import com.campushanjang.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailySelectCountResetScheduler {

    private final UserRepository userRepository;

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void resetDailySelectCounts() {
        try {
            userRepository.resetAllDailySelectCounts();
            log.info("일별 선택 횟수 초기화 완료");
        } catch (Exception e) {
            log.error("선택 횟수 초기화 스케줄러 오류 error={}", e.getMessage(), e);
        }
    }
}
