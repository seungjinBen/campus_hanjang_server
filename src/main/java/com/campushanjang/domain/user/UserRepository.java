package com.campushanjang.domain.user;

import com.campushanjang.domain.user.entity.User;
import com.campushanjang.domain.user.entity.enums.Gender;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    Optional<User> findByReferralCode(String referralCode);

    boolean existsByReferralCode(String referralCode);

    // select() 전용 — 선택 횟수 차감 중 동시 요청으로 인한 초과 방지
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

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

    // k6 부하 테스트 전용 — DevAuthController에서만 호출, prod에서는 Bean 자체가 없음
    List<User> findByKakaoIdStartingWithAndIsActiveTrue(String kakaoIdPrefix, Pageable pageable);

    long countByIsActiveTrue();

    // 자정 스케줄러에서 일괄 초기화 — DailySelectCountResetScheduler 전용
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE User u SET u.dailySelectCount = 0 WHERE u.isActive = true")
    void resetAllDailySelectCounts();

    // Hibernate cascade(CascadeType.ALL)를 우회하여 직접 삭제 — DB의 ON DELETE CASCADE가 연관 테이블을 처리한다.
    // delete(entity) 는 lazy 컬렉션 초기화 → 개별 child DELETE → parent DELETE 순서로 실행해
    // flush 타이밍 문제를 일으킬 수 있어 탈퇴 500 오류가 재현됐다 (2026-09)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM User u WHERE u.id = :id")
    void deleteUserById(@Param("id") UUID id);
}
