package com.campushanjang.domain.photo;

import com.campushanjang.domain.photo.entity.UserPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhotoRepository extends JpaRepository<UserPhoto, UUID> {

    Optional<UserPhoto> findByUserId(UUID userId);

    // N+1 방지용 IN 쿼리 — 여러 유저의 사진을 한 번에 조회
    @Query("SELECT p FROM UserPhoto p WHERE p.user.id IN :userIds")
    List<UserPhoto> findByUserIdIn(@Param("userIds") List<UUID> userIds);
}
