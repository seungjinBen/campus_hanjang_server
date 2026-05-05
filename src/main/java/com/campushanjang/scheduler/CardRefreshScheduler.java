package com.campushanjang.scheduler;

import com.campushanjang.domain.match.DailyCardRepository;
import com.campushanjang.domain.match.MatchService;
import com.campushanjang.domain.user.UserRepository;
import com.campushanjang.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CardRefreshScheduler {

    private final UserRepository userRepository;
    private final MatchService matchService;
    private final DailyCardRepository dailyCardRepository;

    @Scheduled(cron = "0 0 0 * * *")
    public void refreshDailyCards() {
        try {
            LocalDate today = LocalDate.now();

            // 30일 이상 된 카드 정리
            dailyCardRepository.deleteOlderThan(today.minusDays(30));

            List<User> allUsers = userRepository.findAllActive();
            log.info("일별 카드 갱신 시작 userCount={} date={}", allUsers.size(), today);

            int success = 0;
            for (User user : allUsers) {
                try {
                    // 유저별 독립 트랜잭션 — 한 명 실패가 전체에 영향 없음
                    matchService.generateAndSaveDailyCards(user, today);
                    success++;
                } catch (Exception e) {
                    log.error("카드 생성 실패 userId={} error={}", user.getId(), e.getMessage());
                }
            }
            log.info("일별 카드 갱신 완료 successCount={} totalCount={}", success, allUsers.size());
        } catch (Exception e) {
            log.error("카드 갱신 스케줄러 오류 error={}", e.getMessage(), e);
        }
    }
}
