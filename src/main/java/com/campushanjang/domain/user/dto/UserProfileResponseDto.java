package com.campushanjang.domain.user.dto;

import com.campushanjang.domain.user.entity.User;
import com.campushanjang.domain.user.entity.enums.ContactType;
import com.campushanjang.domain.user.entity.enums.Gender;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class UserProfileResponseDto {

    private final UUID id;
    private final String nickname;
    private final Gender gender;
    private final LocalDate birthDate;
    private final String university;
    private final ContactType contactType;
    private final String contactValue;
    // 학과 필터 설정 UI용 — 인증으로 확인된 내 학과와 현재 필터 모드
    private final String verifiedDepartment;
    private final String deptFilterMode;

    public static UserProfileResponseDto of(User user, String decryptedContactValue) {
        return UserProfileResponseDto.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .gender(user.getGender())
                .birthDate(user.getBirthDate())
                .university(user.getUniversity())
                .contactType(user.getContactType())
                .contactValue(decryptedContactValue)
                .verifiedDepartment(user.getVerifiedDepartment())
                .deptFilterMode(user.getDeptFilterMode().name())
                .build();
    }
}
