package com.campushanjang.domain.match;

import com.campushanjang.domain.match.entity.MatchSession;
import com.campushanjang.domain.match.entity.enums.MatchSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<MatchSession, UUID> {

    Optional<MatchSession> findByUserIdAndStatus(UUID userId, MatchSessionStatus status);

    @Query("SELECT s FROM MatchSession s WHERE s.user.id = :userId AND s.nextAvailableAt > :now ORDER BY s.nextAvailableAt DESC")
    Optional<MatchSession> findActiveCooldown(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    @Query("SELECT s FROM MatchSession s WHERE s.nextAvailableAt < :now AND s.status = :status")
    List<MatchSession> findExpiredSessions(
            @Param("now") LocalDateTime now,
            @Param("status") MatchSessionStatus status
    );

    @Query("SELECT mc.candidate.id FROM MatchCandidate mc WHERE mc.session.user.id = :userId AND mc.session.createdAt > :since")
    List<UUID> findRecentCandidateIds(@Param("userId") UUID userId, @Param("since") LocalDateTime since);

    @Query("SELECT s FROM MatchSession s WHERE s.user.id = :userId")
    List<MatchSession> findAllByUserId(@Param("userId") UUID userId);
}
