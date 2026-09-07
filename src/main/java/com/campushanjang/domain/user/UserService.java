package com.campushanjang.domain.user;

import com.campushanjang.common.exception.BusinessException;
import com.campushanjang.common.exception.ErrorCode;
import com.campushanjang.common.security.ResourceOwnerValidator;
import com.campushanjang.common.util.EncryptionUtil;
import com.campushanjang.domain.auth.WithdrawalBlocklistRepository;
import com.campushanjang.domain.auth.entity.WithdrawalBlocklist;
import com.campushanjang.domain.photo.PhotoRepository;
import com.campushanjang.domain.photo.PhotoService;
import com.campushanjang.domain.user.dto.*;
import com.campushanjang.domain.user.entity.IdealTrait;
import com.campushanjang.domain.user.entity.User;
import com.campushanjang.domain.user.entity.UserTrait;
import com.campushanjang.domain.user.entity.enums.DeptFilterMode;
import com.campushanjang.domain.user.entity.enums.TraitKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserTraitRepository userTraitRepository;
    private final IdealTraitRepository idealTraitRepository;
    private final PhotoRepository photoRepository;
    private final PhotoService photoService;
    private final WithdrawalBlocklistRepository withdrawalBlocklistRepository;
    private final ResourceOwnerValidator ownerValidator;

    private static final PolicyFactory SANITIZER = Sanitizers.FORMATTING;

    @Transactional
    public void updateProfile(UUID userId, UserProfileRequestDto request) {
        ownerValidator.validateOwner(userId);
        User user = getUser(userId);

        if (userRepository.existsByNicknameAndIdNot(request.getNickname(), userId)) {
            throw new BusinessException(ErrorCode.NICKNAME_TAKEN);
        }

        String sanitizedNickname = SANITIZER.sanitize(request.getNickname());
        String encryptedContact = EncryptionUtil.encrypt(request.getContactValue());

        // 생년월일·대학·학과는 학생인증(applyStudentVerification)이 관리 — 여기서 덮어쓰지 않는다
        user.updateProfile(
                sanitizedNickname,
                request.getContactType(),
                encryptedContact,
                request.getGender()
        );
        log.info("프로필 업데이트 userId={}", userId);
    }

    @Transactional
    public void updateDeptFilterMode(UUID userId, DeptFilterMode mode) {
        ownerValidator.validateOwner(userId);
        User user = getUser(userId);
        user.updateDeptFilterMode(mode);
        // 오늘 카드는 유지 — 다음 자정 카드 생성부터 적용 (CLAUDE.md 16-4)
        log.info("학과 필터 변경 userId={} mode={}", userId, mode);
    }

    @Transactional(readOnly = true)
    public UserProfileResponseDto getMyProfile(UUID userId) {
        ownerValidator.validateOwner(userId);
        User user = getUser(userId);
        String contactValue = user.getContactValueEncrypted() != null
                ? EncryptionUtil.decrypt(user.getContactValueEncrypted()) : null;
        return UserProfileResponseDto.of(user, contactValue);
    }

    @Transactional
    public void updateTraits(UUID userId, List<TraitRequestDto> traitRequests) {
        ownerValidator.validateOwner(userId);
        User user = getUser(userId);

        for (TraitRequestDto dto : traitRequests) {
            // 학과(MAJOR)는 학생인증에서 확정된 값만 허용 — 클라이언트 입력값은 무시 (위조 방지)
            // 공개 여부(isVisible)만 클라이언트가 결정한다
            String traitValue = (dto.getTraitKey() == TraitKey.MAJOR && user.getVerifiedDepartment() != null)
                    ? user.getVerifiedDepartment()
                    : dto.getTraitValue();

            userTraitRepository.findByUserIdAndTraitKey(userId, dto.getTraitKey())
                    .ifPresentOrElse(
                            trait -> trait.update(traitValue, dto.isVisible()),
                            () -> userTraitRepository.save(UserTrait.builder()
                                    .user(user)
                                    .traitKey(dto.getTraitKey())
                                    .traitValue(traitValue)
                                    .isVisible(dto.isVisible())
                                    .build())
                    );
        }
    }

    @Transactional
    public void updateIdealTraits(UUID userId, List<IdealRequestDto> idealRequests) {
        ownerValidator.validateOwner(userId);
        User user = getUser(userId);

        for (IdealRequestDto dto : idealRequests) {
            if (dto.getTraitValue() == null) {
                idealTraitRepository.deleteByUserIdAndTraitKey(userId, dto.getTraitKey());
            } else {
                idealTraitRepository.findByUserIdAndTraitKey(userId, dto.getTraitKey())
                        .ifPresentOrElse(
                                ideal -> ideal.update(dto.getTraitValue()),
                                () -> idealTraitRepository.save(IdealTrait.builder()
                                        .user(user)
                                        .traitKey(dto.getTraitKey())
                                        .traitValue(dto.getTraitValue())
                                        .build())
                        );
            }
        }
    }

    @Transactional(readOnly = true)
    public ProfileCompleteResponseDto checkProfileComplete(UUID userId) {
        ownerValidator.validateOwner(userId);
        User user = getUser(userId);
        List<String> missing = new ArrayList<>();

        // 학생인증이 온보딩 1단계 — 미인증이면 complete=false 보장
        if (!user.isStudentVerified()) missing.add("verification");
        if (user.getNickname() == null) missing.add("nickname");
        if (user.getGender() == null) missing.add("gender");
        if (user.getBirthDate() == null) missing.add("birthDate");
        if (user.getContactValueEncrypted() == null) missing.add("contactValue");

        boolean hasPhoto = photoRepository.findByUserId(userId).isPresent();
        if (!hasPhoto) missing.add("photo");

        long traitCount = userTraitRepository.countByUserId(userId);
        if (traitCount == 0) missing.add("traits");

        return new ProfileCompleteResponseDto(missing.isEmpty(), missing);
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        ownerValidator.validateOwner(userId);
        User user = getUser(userId);
        withdrawalBlocklistRepository.save(WithdrawalBlocklist.builder()
                .kakaoId(user.getKakaoId())
                .build());
        // 탈퇴 시 프로필 사진 즉시 파기 — DB 행은 CASCADE로 지워지지만 Firebase 파일은 남는다 (개인정보 파기 의무)
        photoService.deleteUserPhotoFromStorage(userId);
        userRepository.delete(user);
        log.info("계정 삭제 userId={}", userId);
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    public record ProfileCompleteResponseDto(boolean complete, List<String> missing) {}
}
