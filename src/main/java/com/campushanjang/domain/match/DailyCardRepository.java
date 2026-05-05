package com.campushanjang.domain.match;

import com.campushanjang.domain.match.entity.DailyCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DailyCardRepository extends JpaRepository<DailyCard, UUID> {

    @Query("SELECT dc FROM DailyCard dc JOIN FETCH dc.candidate WHERE dc.user.id = :userId AND dc.cardDate = :date")
    List<DailyCard> findByUserIdAndDate(@Param("userId") UUID userId, @Param("date") LocalDate date);

    // 최근 30일 내 이미 카드로 노출된 후보 제외 — 중복 노출 방지
    @Query("SELECT dc.candidate.id FROM DailyCard dc WHERE dc.user.id = :userId AND dc.cardDate > :since")
    List<UUID> findRecentCandidateIds(@Param("userId") UUID userId, @Param("since") LocalDate since);

    @Query("SELECT COUNT(dc) FROM DailyCard dc WHERE dc.user.id = :userId AND dc.cardDate = :date")
    int countByUserIdAndDate(@Param("userId") UUID userId, @Param("date") LocalDate date);

    @Modifying
    @Transactional
    @Query("DELETE FROM DailyCard dc WHERE dc.user.id = :userId AND dc.cardDate = :date")
    void deleteByUserIdAndDate(@Param("userId") UUID userId, @Param("date") LocalDate date);

    @Modifying
    @Transactional
    @Query("DELETE FROM DailyCard dc WHERE dc.cardDate < :cutoff")
    void deleteOlderThan(@Param("cutoff") LocalDate cutoff);
}
