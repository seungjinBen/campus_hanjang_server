package com.campushanjang.domain.auth;

import com.campushanjang.domain.auth.entity.WithdrawalBlocklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface WithdrawalBlocklistRepository extends JpaRepository<WithdrawalBlocklist, UUID> {

    boolean existsByKakaoIdHashAndReregistrationAllowedAtAfter(String kakaoIdHash, LocalDateTime now);

    long deleteByReregistrationAllowedAtBefore(LocalDateTime cutoff);
}
