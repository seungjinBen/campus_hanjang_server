package com.campushanjang.domain.match.entity;

import com.campushanjang.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_cards",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "candidate_id", "card_date"}))
@Getter
@NoArgsConstructor
public class DailyCard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private User candidate;

    @Column(name = "match_score", nullable = false)
    private double matchScore;

    @Column(name = "card_date", nullable = false)
    private LocalDate cardDate;

    @Builder
    public DailyCard(User user, User candidate, double matchScore, LocalDate cardDate) {
        this.user = user;
        this.candidate = candidate;
        this.matchScore = matchScore;
        this.cardDate = cardDate;
    }
}
