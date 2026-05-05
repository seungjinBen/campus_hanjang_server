package com.campushanjang.domain.match;

import com.campushanjang.domain.match.entity.MatchCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchCandidateRepository extends JpaRepository<MatchCandidate, UUID> {

    @Query("SELECT mc FROM MatchCandidate mc JOIN FETCH mc.candidate WHERE mc.session.id = :sessionId")
    List<MatchCandidate> findBySessionIdWithCandidate(@Param("sessionId") UUID sessionId);

    Optional<MatchCandidate> findBySessionIdAndCandidateId(UUID sessionId, UUID candidateId);
}
