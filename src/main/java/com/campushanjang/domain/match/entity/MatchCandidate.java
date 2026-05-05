package com.campushanjang.domain.match.entity;

import com.campushanjang.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "match_candidates")
@Getter
@NoArgsConstructor
public class MatchCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private MatchSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private User candidate;

    @Column(name = "match_score", nullable = false)
    private double matchScore;

    @Column(name = "is_chosen", nullable = false)
    private boolean isChosen = false;

    @Builder
    public MatchCandidate(MatchSession session, User candidate, double matchScore) {
        this.session = session;
        this.candidate = candidate;
        this.matchScore = matchScore;
        this.isChosen = false;
    }

    public void choose() {
        this.isChosen = true;
    }
}
