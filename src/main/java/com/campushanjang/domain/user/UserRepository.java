package com.campushanjang.domain.user;

import com.campushanjang.domain.user.entity.User;
import com.campushanjang.domain.user.entity.enums.Gender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByKakaoId(String kakaoId);

    // 개발용 로컬 로그인 — 실서비스 전 삭제 예정
    Optional<User> findByEmail(String email);

    boolean existsByNicknameAndIdNot(String nickname, UUID id);

    boolean existsByNickname(String nickname);

    // JWT 유효성 2차 검증 — 탈퇴·비활성화 계정으로 발급된 토큰 차단
    boolean existsByIdAndIsActiveTrue(UUID id);

    // 프로필 완성 유저만 후보로 허용 — 사진·특징 없는 미완성 계정이 카드에 노출되지 않도록
    @Query("""
            SELECT u FROM User u
            WHERE u.gender = :gender
            AND u.isActive = true
            AND u.nickname IS NOT NULL
            AND u.birthDate IS NOT NULL
            AND EXISTS (SELECT p FROM UserPhoto p WHERE p.user.id = u.id)
            AND EXISTS (SELECT t FROM UserTrait t WHERE t.user.id = u.id)
            AND u.id NOT IN :excludeIds
            """)
    List<User> findActiveCandidates(@Param("gender") Gender gender, @Param("excludeIds") List<UUID> excludeIds);

    @Query("""
            SELECT u FROM User u
            WHERE u.gender = :gender
            AND u.isActive = true
            AND u.nickname IS NOT NULL
            AND u.birthDate IS NOT NULL
            AND EXISTS (SELECT p FROM UserPhoto p WHERE p.user.id = u.id)
            AND EXISTS (SELECT t FROM UserTrait t WHERE t.user.id = u.id)
            """)
    List<User> findActiveByGender(@Param("gender") Gender gender);

    @Query("SELECT u FROM User u WHERE u.isActive = true")
    List<User> findAllActive();

    // 자정 스케줄러에서 일괄 초기화 — DailySelectCountResetScheduler 전용
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE User u SET u.dailySelectCount = 0 WHERE u.isActive = true")
    void resetAllDailySelectCounts();
}
