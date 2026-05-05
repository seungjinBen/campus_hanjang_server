package com.campushanjang.domain.match.entity;

import com.campushanjang.domain.match.entity.enums.SelectionType;
import com.campushanjang.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "selections")
@Getter
@NoArgsConstructor
public class Selection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selector_id", nullable = false)
    private User selector;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_id", nullable = false)
    private User selected;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private SelectionType type;

    @Column(name = "match_score")
    private Double matchScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Selection(User selector, User selected, SelectionType type, Double matchScore) {
        this.selector = selector;
        this.selected = selected;
        this.type = type;
        this.matchScore = matchScore;
        this.createdAt = LocalDateTime.now();
    }
}
