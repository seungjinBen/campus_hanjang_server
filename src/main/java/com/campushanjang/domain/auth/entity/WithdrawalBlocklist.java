package com.campushanjang.domain.auth.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "withdrawal_blocklist")
@Getter
@NoArgsConstructor
public class WithdrawalBlocklist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "kakao_id", nullable = false, length = 50)
    private String kakaoId;

    @Column(name = "withdrawn_at", nullable = false)
    private LocalDateTime withdrawnAt;

    // 탈퇴 후 24시간 재가입 차단 — 매칭 시스템 악용 방지
    @Column(name = "reregistration_allowed_at", nullable = false)
    private LocalDateTime reregistrationAllowedAt;

    @Builder
    public WithdrawalBlocklist(String kakaoId) {
        this.kakaoId = kakaoId;
        this.withdrawnAt = LocalDateTime.now();
        this.reregistrationAllowedAt = this.withdrawnAt.plusHours(24);
    }
}
