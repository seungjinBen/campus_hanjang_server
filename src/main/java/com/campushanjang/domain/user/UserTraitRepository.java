package com.campushanjang.domain.user;

import com.campushanjang.domain.user.entity.UserTrait;
import com.campushanjang.domain.user.entity.enums.TraitKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserTraitRepository extends JpaRepository<UserTrait, UUID> {

    Optional<UserTrait> findByUserIdAndTraitKey(UUID userId, TraitKey traitKey);

    List<UserTrait> findByUserId(UUID userId);

    long countByUserId(UUID userId);

    // N+1 방지용 IN 쿼리 — 여러 유저의 특징을 한 번에 조회
    @Query("SELECT t FROM UserTrait t WHERE t.user.id IN :userIds")
    List<UserTrait> findByUserIdIn(@Param("userIds") List<UUID> userIds);
}
