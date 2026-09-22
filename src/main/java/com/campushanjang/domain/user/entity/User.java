package com.campushanjang.domain.user.entity;

import com.campushanjang.domain.user.entity.enums.ContactType;
import com.campushanjang.domain.user.entity.enums.DeptFilterMode;
import com.campushanjang.domain.user.entity.enums.Gender;
import com.campushanjang.domain.user.entity.enums.UserRole;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "kakao_id", nullable = false, unique = true, length = 50)
    private String kakaoId;

    // 개발용 로컬 로그인 — 실서비스 전 삭제 예정
    @Column(name = "email", unique = true, length = 100)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "nickname", length = 20)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "university", length = 100)
    private String university;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type")
    private ContactType contactType;

    @Column(name = "contact_value_encrypted")
    private String contactValueEncrypted;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "daily_select_count", nullable = false)
    private int dailySelectCount = 0;

    // 학생인증 상태 — QR 캡처 에이전트 승인 시 true
    @Column(name = "is_student_verified", nullable = false)
    private boolean isStudentVerified = false;

    @Column(name = "verified_university", length = 100)
    private String verifiedUniversity;

    // 인증으로 확인된 학과 — 매칭 필터용. 프로필 노출 여부는 UserTrait(MAJOR)의 isVisible이 담당
    @Column(name = "verified_department", length = 100)
    private String verifiedDepartment;

    // 매칭 카드 풀 학과 필터 (전체 / 같은 과만 / 같은 과 제외)
    @Enumerated(EnumType.STRING)
    @Column(name = "dept_filter_mode", nullable = false, length = 20)
    private DeptFilterMode deptFilterMode = DeptFilterMode.ALL;

    // 얼리버드: 사전등록 기간 내 학생인증 승인 완료 — 운영기간 내내 하루 선택 +1 (CLAUDE.md 16-5)
    @Column(name = "is_early_bird", nullable = false)
    private boolean isEarlyBird = false;

    // 내 초대 코드 — 첫 공유 요청 시 lazy 생성
    @Column(name = "referral_code", unique = true, length = 12)
    private String referralCode;

    // 나를 초대한 유저 — 가입 시 1회만 기록
    @Column(name = "referred_by")
    private UUID referredBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login_at", nullable = false)
    private LocalDateTime lastLoginAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private UserRole role = UserRole.USER;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserTrait> traits = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<IdealTrait> idealTraits = new ArrayList<>();

    @Builder
    public User(String kakaoId, String email, String passwordHash,
                String nickname, Gender gender, LocalDate birthDate,
                String university, ContactType contactType, String contactValueEncrypted, UserRole role) {
        this.kakaoId = kakaoId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.gender = gender;
        this.birthDate = birthDate;
        this.university = university;
        this.contactType = contactType;
        this.contactValueEncrypted = contactValueEncrypted;
        this.role = role != null ? role : UserRole.USER;
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
        this.lastLoginAt = LocalDateTime.now();
    }

    public void assignAdminRole() {
        this.role = UserRole.ADMIN;
    }

    // 생년월일·대학은 학생인증(applyStudentVerification)이 관리 — 프로필 수정으로 덮어쓸 수 없다
    public void updateProfile(String nickname, ContactType contactType,
                              String contactValueEncrypted, Gender gender) {
        this.nickname = nickname;
        this.contactType = contactType;
        this.contactValueEncrypted = contactValueEncrypted;
        this.gender = gender;
    }

    public void updateLastLoginAt() {
        this.lastLoginAt = LocalDateTime.now();
    }

    // 학생인증 승인 시 추출 정보로 프로필 자동 채움 — 온보딩 입력 단계 축소
    public void applyStudentVerification(String university, String department, LocalDate birthDate) {
        this.isStudentVerified = true;
        this.verifiedUniversity = university;
        this.verifiedDepartment = department;
        this.university = university;
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
    }

    // 에브리타임 인증 경로 전용 — 화면에 없는 학과·생년월일을 승인 후 1회만 보충 입력.
    // 재호출 방지는 서비스 계층의 birthDate IS NULL 가드가 담당 (CLAUDE.md 5)
    public void applySupplementaryVerificationInfo(String department, LocalDate birthDate) {
        this.verifiedDepartment = department;
        this.birthDate = birthDate;
    }

    public void updateDeptFilterMode(DeptFilterMode mode) {
        this.deptFilterMode = mode;
    }

    public void markEarlyBird() {
        this.isEarlyBird = true;
    }

    public void assignReferralCode(String code) {
        this.referralCode = code;
    }

    public void applyReferredBy(UUID referrerId) {
        this.referredBy = referrerId;
    }

    public void incrementSelectCount() {
        this.dailySelectCount++;
    }

    public void resetDailySelectCount() {
        this.dailySelectCount = 0;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
