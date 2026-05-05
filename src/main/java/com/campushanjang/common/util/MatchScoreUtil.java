package com.campushanjang.common.util;

import com.campushanjang.domain.user.entity.IdealTrait;
import com.campushanjang.domain.user.entity.UserTrait;
import com.campushanjang.domain.user.entity.enums.TraitKey;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MatchScoreUtil {

    public static double calculateMatchScore(
            List<IdealTrait> myIdeals,
            List<UserTrait> theirTraits
    ) {
        return calculateMatchScore(myIdeals, theirTraits, null, null);
    }

    public static double calculateMatchScore(
            List<IdealTrait> myIdeals,
            List<UserTrait> theirTraits,
            LocalDate myBirthDate,
            LocalDate theirBirthDate
    ) {
        List<IdealTrait> activeIdeals = myIdeals.stream()
                .filter(i -> i.getTraitValue() != null)
                .toList();

        // 조건을 명시하지 않은 유저는 기본 점수
        if (activeIdeals.isEmpty()) {
            return 0.3;
        }

        long matched = activeIdeals.stream()
                .filter(ideal -> {
                    if (ideal.getTraitKey() == TraitKey.AGE_PREFERENCE) {
                        // 나이 이상형은 생년월일 비교가 필요 — UserTrait에 저장되지 않으므로 별도 처리
                        return matchesAgePreference(ideal.getTraitValue(), myBirthDate, theirBirthDate);
                    }
                    return isMatch(ideal, theirTraits);
                })
                .count();

        return (double) matched / activeIdeals.size();
    }

    // 연상/연하/동갑 — 이상형 보유자(myBirthDate) 기준으로 평가받는 사람(theirBirthDate)의 관계 판정
    private static boolean matchesAgePreference(String idealValue, LocalDate myBirthDate, LocalDate theirBirthDate) {
        if (myBirthDate == null || theirBirthDate == null) return false;
        int myYear = myBirthDate.getYear();
        int theirYear = theirBirthDate.getYear();
        String relationship;
        if (theirYear < myYear) {
            relationship = "연상";
        } else if (theirYear > myYear) {
            relationship = "연하";
        } else {
            relationship = "동갑";
        }
        Set<String> idealRelationships = Arrays.stream(idealValue.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
        return idealRelationships.contains(relationship);
    }

    private static boolean isMatch(IdealTrait ideal, List<UserTrait> theirTraits) {
        return theirTraits.stream()
                .filter(t -> t.getTraitKey() == ideal.getTraitKey())
                .findFirst()
                .map(t -> matchesByKey(ideal.getTraitKey(), ideal.getTraitValue(), t.getTraitValue()))
                .orElse(false);
    }

    private static boolean matchesByKey(TraitKey key, String idealValue, String theirValue) {
        return switch (key) {
            case HEIGHT -> {
                try {
                    int idealHeight = Integer.parseInt(idealValue.trim());
                    int theirHeight = Integer.parseInt(theirValue.trim());
                    yield theirHeight >= idealHeight;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case MBTI -> {
                // 이상형 MBTI는 E/I/T/F 계열을 쉼표로 구분해 저장 (예: "E,T")
                // 선택한 모든 계열이 일치해야 매칭 (AND 조건)
                String upper = theirValue.trim().toUpperCase();
                String[] categories = idealValue.split(",");
                boolean allMatch = true;
                for (String cat : categories) {
                    boolean ok = switch (cat.trim().toUpperCase()) {
                        case "E" -> upper.startsWith("E");
                        case "I" -> upper.startsWith("I");
                        // MBTI 3번째 글자(0-indexed 2)가 T 또는 F
                        case "T" -> upper.length() >= 3 && upper.charAt(2) == 'T';
                        case "F" -> upper.length() >= 3 && upper.charAt(2) == 'F';
                        default -> false;
                    };
                    if (!ok) {
                        allMatch = false;
                        break;
                    }
                }
                yield allMatch;
            }
            case ANIMAL_FACE -> {
                // 이상형 동물상, 내 동물상 모두 복수 선택 가능 (쉼표 구분) — 교집합이 하나라도 있으면 매칭
                Set<String> idealFaces = Arrays.stream(idealValue.split(","))
                        .map(String::trim).map(String::toLowerCase)
                        .collect(Collectors.toSet());
                Set<String> theirFaces = Arrays.stream(theirValue.split(","))
                        .map(String::trim).map(String::toLowerCase)
                        .collect(Collectors.toSet());
                theirFaces.retainAll(idealFaces);
                yield !theirFaces.isEmpty();
            }
            case HOBBY -> {
                // 취미는 쉼표 구분 복수 선택, 교집합이 하나라도 있으면 매칭
                Set<String> idealHobbies = Arrays.stream(idealValue.split(","))
                        .map(String::trim).map(String::toLowerCase)
                        .collect(Collectors.toSet());
                Set<String> theirHobbies = Arrays.stream(theirValue.split(","))
                        .map(String::trim).map(String::toLowerCase)
                        .collect(Collectors.toSet());
                idealHobbies.retainAll(theirHobbies);
                yield !idealHobbies.isEmpty();
            }
            case MAJOR -> theirValue.toLowerCase().contains(idealValue.toLowerCase());
            case DRINKING -> idealValue.equalsIgnoreCase(theirValue);
            // 흡연 여부는 정확히 일치해야 매칭
            case SMOKING -> idealValue.equalsIgnoreCase(theirValue);
            // AGE_PREFERENCE는 matchesAgePreference에서 별도 처리 — 여기 도달하지 않음
            case AGE_PREFERENCE -> false;
        };
    }
}
