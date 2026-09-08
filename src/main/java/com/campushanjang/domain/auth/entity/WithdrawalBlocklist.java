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

    // HMAC-SHA256 해시 — 평문 카카오 ID를 목적(재가입 차단) 이상으로 보존하지 않는다
    @Column(name = "kakao_id_hash", nullable = false, length = 64)
    private String kakaoIdHash;

    @Column(name = "withdrawn_at", nullable = false)
    private LocalDateTime withdrawnAt;

    // 탈퇴 후 24시간 재가입 차단 — 매칭 시스템 악용 방지
    @Column(name = "reregistration_allowed_at", nullable = false)
    private LocalDateTime reregistrationAllowedAt;

    @Builder
    public WithdrawalBlocklist(String kakaoIdHash) {
        this.kakaoIdHash = kakaoIdHash;
        this.withdrawnAt = LocalDateTime.now();
        this.reregistrationAllowedAt = this.withdrawnAt.plusHours(24);
    }
}
