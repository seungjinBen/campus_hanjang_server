package com.campushanjang.domain.photo;

import com.campushanjang.domain.photo.entity.UserPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PhotoRepository extends JpaRepository<UserPhoto, UUID> {

    Optional<UserPhoto> findByUserId(UUID userId);
}
