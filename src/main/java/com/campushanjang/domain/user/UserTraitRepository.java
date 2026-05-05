package com.campushanjang.domain.user;

import com.campushanjang.domain.user.entity.UserTrait;
import com.campushanjang.domain.user.entity.enums.TraitKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserTraitRepository extends JpaRepository<UserTrait, UUID> {

    Optional<UserTrait> findByUserIdAndTraitKey(UUID userId, TraitKey traitKey);

    List<UserTrait> findByUserId(UUID userId);

    long countByUserId(UUID userId);
}
