package com.campushanjang.domain.referral.entity;

import com.campushanjang.domain.referral.entity.enums.ReferralStatus;
import com.campushanjang.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "referral_events")
@Getter
@NoArgsConstructor
public class ReferralEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referrer_id", nullable = false)
    private User referrer;

    // 피초대자는 평생 1회만 초대받음 (DB UNIQUE)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referee_id", nullable = false, unique = true)
    private User referee;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReferralStatus status;

    @Column(name = "rewarded_at")
    private LocalDateTime rewardedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ReferralEvent(User referrer, User referee) {
        this.referrer = referrer;
        this.referee = referee;
        this.status = ReferralStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    // 피초대자의 학생인증 승인 시점에 호출 — rewarded_at 날짜가 곧 보너스 유효일
    public void reward() {
        this.status = ReferralStatus.REWARDED;
        this.rewardedAt = LocalDateTime.now();
    }
}
