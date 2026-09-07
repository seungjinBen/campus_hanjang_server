package com.campushanjang.domain.auth;

import com.campushanjang.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AuthRepository extends JpaRepository<RefreshToken, UUID> {

    List<RefreshToken> findByUserIdAndIsRevokedFalse(UUID userId);

    List<RefreshToken> findByUserId(UUID userId);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.isRevoked = true WHERE r.user.id = :userId AND r.isRevoked = false")
    void revokeAllByUserId(@Param("userId") UUID userId);
}
