package com.campushanjang.domain.match.entity;

import com.campushanjang.domain.match.entity.enums.MatchSessionStatus;
import com.campushanjang.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "match_sessions")
@Getter
@NoArgsConstructor
public class MatchSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MatchSessionStatus status;

    @Column(name = "next_available_at", nullable = false)
    private LocalDateTime nextAvailableAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MatchCandidate> candidates = new ArrayList<>();

    @Builder
    public MatchSession(User user, LocalDateTime nextAvailableAt) {
        this.user = user;
        this.status = MatchSessionStatus.PENDING;
        this.nextAvailableAt = nextAvailableAt;
        this.createdAt = LocalDateTime.now();
    }

    public void select(LocalDateTime nextAvailableAt) {
        this.status = MatchSessionStatus.SELECTED;
        this.nextAvailableAt = nextAvailableAt;
    }

    public void waitForNext(LocalDateTime nextAvailableAt) {
        this.status = MatchSessionStatus.WAITING;
        this.nextAvailableAt = nextAvailableAt;
    }

    public void expire() {
        this.status = MatchSessionStatus.EXPIRED;
    }
}
