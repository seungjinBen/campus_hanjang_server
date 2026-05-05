package com.campushanjang.domain.user.entity;

import com.campushanjang.domain.user.entity.enums.TraitKey;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "user_traits")
@Getter
@NoArgsConstructor
public class UserTrait {

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

    @Column(name = "trait_value", nullable = false, length = 100)
    private String traitValue;

    @Column(name = "is_visible", nullable = false)
    private boolean isVisible = true;

    @Builder
    public UserTrait(User user, TraitKey traitKey, String traitValue, boolean isVisible) {
        this.user = user;
        this.traitKey = traitKey;
        this.traitValue = traitValue;
        this.isVisible = isVisible;
    }

    public void update(String traitValue, boolean isVisible) {
        this.traitValue = traitValue;
        this.isVisible = isVisible;
    }
}
