package com.campushanjang.domain.user;

import com.campushanjang.domain.user.entity.IdealTrait;
import com.campushanjang.domain.user.entity.enums.TraitKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdealTraitRepository extends JpaRepository<IdealTrait, UUID> {

    Optional<IdealTrait> findByUserIdAndTraitKey(UUID userId, TraitKey traitKey);

    List<IdealTrait> findByUserId(UUID userId);

    @Transactional
    void deleteByUserIdAndTraitKey(UUID userId, TraitKey traitKey);
}
