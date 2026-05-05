package com.campushanjang.domain.user.entity;

import com.campushanjang.domain.user.entity.enums.TraitKey;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "ideal_traits")
@Getter
@NoArgsConstructor
public class IdealTrait {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "trait_key", nullable = false)
    private TraitKey traitKey;

    @Column(name = "trait_value", length = 100)
    private String traitValue;

    @Builder
    public IdealTrait(User user, TraitKey traitKey, String traitValue) {
        this.user = user;
        this.traitKey = traitKey;
        this.traitValue = traitValue;
    }

    public void update(String traitValue) {
        this.traitValue = traitValue;
    }
}
