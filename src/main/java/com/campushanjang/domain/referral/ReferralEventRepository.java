package com.campushanjang.domain.referral;

import com.campushanjang.domain.referral.entity.ReferralEvent;
import com.campushanjang.domain.referral.entity.enums.ReferralStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ReferralEventRepository extends JpaRepository<ReferralEvent, UUID> {

    // 인증 승인 시 PENDING → REWARDED 전환용
    Optional<ReferralEvent> findByRefereeIdAndStatus(UUID refereeId, ReferralStatus status);

    boolean existsByRefereeId(UUID refereeId);

    // 오늘 리퍼럴 보너스 활성 여부 — rewarded_at이 오늘 범위면 +1 (하루 1회 상한은 존재 여부로 판정)
    @Query("""
            SELECT COUNT(e) > 0 FROM ReferralEvent e
            WHERE e.referrer.id = :referrerId
            AND e.status = 'REWARDED'
            AND e.rewardedAt >= :startOfDay AND e.rewardedAt < :endOfDay
            """)
    boolean hasRewardBetween(@Param("referrerId") UUID referrerId,
                             @Param("startOfDay") LocalDateTime startOfDay,
                             @Param("endOfDay") LocalDateTime endOfDay);
}
