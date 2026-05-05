package com.campushanjang.domain.admin;

import com.campushanjang.common.exception.BusinessException;
import com.campushanjang.common.exception.ErrorCode;
import com.campushanjang.common.util.EncryptionUtil;
import com.campushanjang.domain.admin.dto.AdminCreateUserRequestDto;
import com.campushanjang.domain.admin.dto.AdminCreateUserResponseDto;
import com.campushanjang.domain.photo.PhotoService;
import com.campushanjang.domain.photo.dto.PhotoUploadResponseDto;
import com.campushanjang.domain.user.IdealTraitRepository;
import com.campushanjang.domain.user.UserRepository;
import com.campushanjang.domain.user.UserTraitRepository;
import com.campushanjang.domain.user.entity.IdealTrait;
import com.campushanjang.domain.user.entity.User;
import com.campushanjang.domain.user.entity.UserTrait;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final UserTraitRepository userTraitRepository;
    private final IdealTraitRepository idealTraitRepository;
    private final PhotoService photoService;

    @Transactional
    public AdminCreateUserResponseDto createUser(AdminCreateUserRequestDto request) {
        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_TAKEN);
        }

        List<AdminCreateUserRequestDto.TraitEntry> nonEmptyTraits = request.traits().stream()
                .filter(t -> t.traitValue() != null && !t.traitValue().isBlank())
                .toList();

        if (nonEmptyTraits.isEmpty()) {
            throw new BusinessException(ErrorCode.PROFILE_INCOMPLETE);
        }

        // 카카오 계정 없이 관리자가 직접 입력한 유저는 합성 ID 사용
        String syntheticKakaoId = "ADMIN_" + UUID.randomUUID();
        String encryptedContact = EncryptionUtil.encrypt(request.contactValue());

        User user = User.builder()
                .kakaoId(syntheticKakaoId)
                .nickname(request.nickname())
                .gender(request.gender())
                .birthDate(request.birthDate())
                .university(request.university())
                .contactType(request.contactType())
                .contactValueEncrypted(encryptedContact)
                .build();

        userRepository.save(user);

        nonEmptyTraits.forEach(t -> userTraitRepository.save(
                UserTrait.builder()
                        .user(user)
                        .traitKey(t.traitKey())
                        .traitValue(t.traitValue())
                        .isVisible(t.isVisible())
                        .build()
        ));

        if (request.ideals() != null) {
            request.ideals().stream()
                    .filter(i -> i.traitValue() != null && !i.traitValue().isBlank())
                    .forEach(i -> idealTraitRepository.save(
                            IdealTrait.builder()
                                    .user(user)
                                    .traitKey(i.traitKey())
                                    .traitValue(i.traitValue())
                                    .build()
                    ));
        }

        log.info("관리자 유저 생성 userId={} nickname={} traitCount={}", user.getId(), user.getNickname(), nonEmptyTraits.size());
        return new AdminCreateUserResponseDto(user.getId(), user.getNickname());
    }

    public PhotoUploadResponseDto uploadPhotoForUser(UUID userId, MultipartFile file) throws IOException {
        return photoService.upload(userId, file);
    }
}
