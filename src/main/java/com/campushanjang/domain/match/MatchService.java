package com.campushanjang.domain.match;

import com.campushanjang.common.exception.BusinessException;
import com.campushanjang.common.exception.ErrorCode;
import com.campushanjang.common.util.EncryptionUtil;
import com.campushanjang.common.util.MatchScoreUtil;
import com.campushanjang.domain.match.dto.*;
import com.campushanjang.domain.match.entity.DailyCard;
import com.campushanjang.domain.match.entity.Note;
import com.campushanjang.domain.match.entity.Selection;
import com.campushanjang.domain.match.entity.enums.NoteStatus;
import com.campushanjang.domain.match.entity.enums.SelectionType;
import com.campushanjang.domain.photo.PhotoRepository;
import com.campushanjang.domain.referral.ReferralEventRepository;
import com.campushanjang.domain.user.IdealTraitRepository;
import com.campushanjang.domain.user.UserRepository;
import com.campushanjang.domain.user.UserTraitRepository;
import com.campushanjang.domain.user.entity.IdealTrait;
import com.campushanjang.domain.user.entity.User;
import com.campushanjang.domain.user.entity.UserTrait;
import com.campushanjang.domain.user.entity.enums.DeptFilterMode;
import com.campushanjang.domain.user.entity.enums.Gender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final DailyCardRepository dailyCardRepository;
    private final SelectionRepository selectionRepository;
    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final UserTraitRepository userTraitRepository;
    private final IdealTraitRepository idealTraitRepository;
    private final PhotoRepository photoRepository;
    private final ReferralEventRepository referralEventRepository;

    private static final PolicyFactory SANITIZER = Sanitizers.FORMATTING;

    // 남녀 모두 하루 10장 — 카드 희소성 규칙
    private static final int DAILY_CARD_LIMIT = 10;
    // 하루 기본 선택 횟수 — 얼리버드(+1)·리퍼럴 보너스(+1)로 최대 4회 (CLAUDE.md 16-5, 2026 가을 개정)
    private static final int BASE_DAILY_SELECT_LIMIT = 2;
    // 일치율 75% 기준 — 연락처 즉시 공개 vs 쪽지 분기 임계값
    private static final double CONTACT_REVEAL_THRESHOLD = 0.75;
    // 서비스 공식 오픈일 — 이전에 스케줄러가 생성한 카드가 30일 제외 풀에 포함되지 않도록 하한선으로 사용
    private static final LocalDate SERVICE_LAUNCH_DATE = LocalDate.of(2026, 5, 19);
    @Transactional
    public DailyCardsResponseDto getTodayCards(UUID userId) {
        User user = getUser(userId);
        requireStudentVerified(user);
        LocalDate today = LocalDate.now();

        List<DailyCard> cards = dailyCardRepository.findByUserIdAndDate(userId, today);

        // 카드가 아예 없는 경우(자정 이후 신규 가입자)에만 온디맨드 생성
        // 탈퇴로 줄어든 카드(예: 9장)는 보충하지 않음 — cards.size() > 0 이면 그대로 유지
        if (cards.isEmpty()) {
            cards = generateCardsInternal(user, today);
        }

        int dailyLimit = dailySelectLimit(user);
        int remaining = Math.max(0, dailyLimit - user.getDailySelectCount());

        return DailyCardsResponseDto.builder()
                .cards(buildCardDtos(cards))
                .remainingSelectCount(remaining)
                .dailySelectLimit(dailyLimit)
                .build();
    }

    @Transactional
    public SelectResponseDto select(UUID userId, UUID candidateId) {
        // 비관적 락 — 동시 요청이 dailySelectCount를 동시에 읽어 초과 선택하는 경쟁 조건 방지
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        requireStudentVerified(user);

        // 오늘의 카드에 포함된 후보인지 확인
        List<DailyCard> todayCards = dailyCardRepository.findByUserIdAndDate(userId, LocalDate.now());
        DailyCard chosenCard = todayCards.stream()
                .filter(dc -> dc.getCandidate().getId().equals(candidateId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_NOT_FOUND));

        User selected = chosenCard.getCandidate();

        // 이미 선택한 상대 재선택 시 횟수 차감 없이 동일한 결과 반환
        if (selectionRepository.existsBySelectorIdAndSelectedId(userId, candidateId)) {
            log.info("재선택(횟수 차감 없음) selectorId={} selectedId={}", userId, candidateId);
            return buildSelectResponse(chosenCard, selected);
        }

        // 하루 선택 한도 초과 방지 — 유저별 동적 한도 (기본 2 + 얼리버드 + 리퍼럴)
        if (user.getDailySelectCount() >= dailySelectLimit(user)) {
            throw new BusinessException(ErrorCode.DAILY_SELECT_LIMIT_EXCEEDED);
        }

        user.incrementSelectCount();

        if (chosenCard.getMatchScore() >= CONTACT_REVEAL_THRESHOLD) {
            selectionRepository.save(Selection.builder()
                    .selector(user)
                    .selected(selected)
                    .type(SelectionType.CONTACT_REVEALED)
                    .matchScore(chosenCard.getMatchScore())
                    .build());
            log.info("연락처 공개 selectorId={} selectedId={} score={}", userId, candidateId, chosenCard.getMatchScore());
        } else {
            // NOTE_REQUIRED: Selection을 즉시 저장해야 더블탭·네트워크 재시도 시 멱등성 가드가 동작한다.
            // 저장하지 않으면 existsBySelectorIdAndSelectedId가 항상 false를 반환해 횟수가 중복 차감된다.
            selectionRepository.save(Selection.builder()
                    .selector(user)
                    .selected(selected)
                    .type(SelectionType.NOTE_SENT)
                    .matchScore(chosenCard.getMatchScore())
                    .build());
            log.info("쪽지 유도 selectorId={} selectedId={} score={}", userId, candidateId, chosenCard.getMatchScore());
        }

        return buildSelectResponse(chosenCard, selected);
    }

    private SelectResponseDto buildSelectResponse(DailyCard chosenCard, User selected) {
        if (chosenCard.getMatchScore() >= CONTACT_REVEAL_THRESHOLD) {
            // 연락처 복호화 — 70% 이상 일치 시에만 허용
            String contactValue = selected.getContactValueEncrypted() != null
                    ? EncryptionUtil.decrypt(selected.getContactValueEncrypted()) : null;
            return SelectResponseDto.builder()
                    .type("CONTACT_REVEALED")
                    .message("당신이 상대방의 이상형이에요! 💘 연락처를 확인해보세요.")
                    .selectedId(selected.getId())
                    .contactType(selected.getContactType() != null ? selected.getContactType().name() : null)
                    .contactValue(contactValue)
                    .build();
        } else {
            return SelectResponseDto.builder()
                    .type("NOTE_REQUIRED")
                    .message("아직 상대방의 이상형 조건에 완전히 맞지 않아요. 쪽지로 먼저 마음을 전해보세요! 💌")
                    .selectedId(selected.getId())
                    .build();
        }
    }

    @Transactional
    public void sendNote(UUID userId, UUID selectedId, String rawContent) {
        User selector = getUser(userId);
        requireStudentVerified(selector);
        User selected = userRepository.findById(selectedId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_NOT_FOUND));

        // select()를 먼저 호출한 경우에만 쪽지 전송 허용 — NOTE_SENT Selection이 select()에서 생성됨
        if (!selectionRepository.existsBySelectorIdAndSelectedIdAndType(userId, selectedId, SelectionType.NOTE_SENT)) {
            throw new BusinessException(ErrorCode.CANDIDATE_NOT_FOUND);
        }

        // 동일 상대에게 쪽지 중복 전송 방지
        if (noteRepository.existsBySelectorIdAndSelectedId(userId, selectedId)) {
            throw new BusinessException(ErrorCode.NOTE_ALREADY_SENT);
        }

        String content = SANITIZER.sanitize(rawContent);

        // 공백만 있는 쪽지 거부
        if (content.isBlank()) {
            throw new BusinessException(ErrorCode.NOTE_TOO_LONG);
        }

        // 쪽지 50자 초과 저장 방지
        if (content.length() > 50) {
            throw new BusinessException(ErrorCode.NOTE_TOO_LONG);
        }

        noteRepository.save(Note.builder()
                .selector(selector)
                .selected(selected)
                .noteContent(content)
                .build());

        // Selection은 select()에서 이미 저장됨 — 여기서 추가 저장하면 중복 레코드 발생
        log.info("쪽지 전송 selectorId={} selectedId={}", userId, selectedId);
    }

    @Transactional(readOnly = true)
    public List<ReceivedContactDto> getReceivedContacts(UUID userId) {
        List<Selection> selections = selectionRepository.findBySelectedIdAndType(userId, SelectionType.CONTACT_REVEALED);
        if (selections.isEmpty()) return List.of();

        List<UUID> selectorIds = selections.stream()
                .map(s -> s.getSelector().getId())
                .collect(Collectors.toList());

        Map<UUID, String> photoUrlMap = buildPhotoUrlMap(selectorIds);
        Map<UUID, List<MatchCardResponseDto.TraitDto>> traitMap =
                buildTraitDtoMap(userTraitRepository.findByUserIdIn(selectorIds));

        return selections.stream()
                .map(selection -> {
                    User selector = selection.getSelector();
                    UUID selectorId = selector.getId();
                    return ReceivedContactDto.builder()
                            .selectorId(selectorId)
                            .nickname(selector.getNickname())
                            .photoUrl(photoUrlMap.get(selectorId))
                            .visibleTraits(traitMap.getOrDefault(selectorId, List.of()))
                            .selectedAt(selection.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReceivedNoteDto> getReceivedNotes(UUID userId) {
        List<Note> notes = noteRepository.findBySelectedIdWithSelector(userId).stream()
                .filter(note -> note.getStatus() != NoteStatus.REJECTED)
                .collect(Collectors.toList());

        if (notes.isEmpty()) return List.of();

        List<UUID> selectorIds = notes.stream()
                .map(note -> note.getSelector().getId())
                .collect(Collectors.toList());

        Map<UUID, String> photoUrlMap = buildPhotoUrlMap(selectorIds);
        Map<UUID, List<MatchCardResponseDto.TraitDto>> traitMap =
                buildTraitDtoMap(userTraitRepository.findByUserIdIn(selectorIds));

        return notes.stream()
                .map(note -> {
                    User selector = note.getSelector();
                    UUID selectorId = selector.getId();
                    ReceivedNoteDto.ReceivedNoteDtoBuilder builder = ReceivedNoteDto.builder()
                            .noteId(note.getId())
                            .status(note.getStatus().name())
                            .noteContent(note.getNoteContent())
                            .sentAt(note.getCreatedAt())
                            .respondedAt(note.getRespondedAt())
                            .selectorId(selectorId)
                            .nickname(selector.getNickname())
                            .photoUrl(photoUrlMap.get(selectorId))
                            .visibleTraits(traitMap.getOrDefault(selectorId, List.of()));

                    // 연락처는 수락 후에만 공개 — 거절 시 연락처 비노출이 곧 익명 보호
                    if (note.getStatus() == NoteStatus.ACCEPTED) {
                        String contactValue = selector.getContactValueEncrypted() != null
                                ? EncryptionUtil.decrypt(selector.getContactValueEncrypted()) : null;
                        builder.selectorContactType(selector.getContactType() != null ? selector.getContactType().name() : null)
                                .selectorContactValue(contactValue);
                    }

                    return builder.build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public NoteRespondResponseDto respondToNote(UUID userId, UUID noteId, String action) {
        Note note = noteRepository.findByIdWithAll(noteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTE_NOT_FOUND));

        if (!note.getSelected().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (note.getStatus() != NoteStatus.PENDING) {
            throw new BusinessException(ErrorCode.NOTE_ALREADY_RESPONDED);
        }

        NoteStatus targetStatus = NoteStatus.valueOf(action);

        if (targetStatus == NoteStatus.ACCEPTED) {
            note.accept();
            User selector = note.getSelector();
            // 연락처 복호화 — 쪽지 수락 시 양방향 연락처 공개
            String contactValue = selector.getContactValueEncrypted() != null
                    ? EncryptionUtil.decrypt(selector.getContactValueEncrypted()) : null;

            log.info("쪽지 수락 noteId={} selectorId={} selectedId={}", noteId, selector.getId(), userId);
            return NoteRespondResponseDto.builder()
                    .action("ACCEPTED")
                    .message("쪽지를 수락했어요! 상대방 연락처를 확인해보세요.")
                    .selectorId(selector.getId())
                    .nickname(selector.getNickname())
                    .photoUrl(getPhotoUrl(selector.getId()))
                    .visibleTraits(buildVisibleTraits(selector.getId()))
                    .selectorContactType(selector.getContactType() != null ? selector.getContactType().name() : null)
                    .selectorContactValue(contactValue)
                    .build();
        } else {
            note.reject();
            log.info("쪽지 거절 noteId={} selectedId={}", noteId, userId);
            return NoteRespondResponseDto.builder()
                    .action("REJECTED")
                    .message("쪽지를 거절했어요.")
                    .build();
        }
    }

    @Transactional(readOnly = true)
    public List<SentNoteDto> getSentNotes(UUID userId) {
        return noteRepository.findBySelectorIdWithSelected(userId).stream()
                .map(note -> {
                    User selected = note.getSelected();
                    SentNoteDto.SentNoteDtoBuilder builder = SentNoteDto.builder()
                            .noteId(note.getId())
                            .selectedId(selected.getId())
                            .noteContent(note.getNoteContent())
                            .status(note.getStatus().name())
                            .sentAt(note.getCreatedAt())
                            .respondedAt(note.getRespondedAt());

                    // 수락된 경우에만 상대방 연락처 공개 — 양방향 연락처 공개 규칙
                    if (note.getStatus() == NoteStatus.ACCEPTED) {
                        String contactValue = selected.getContactValueEncrypted() != null
                                ? EncryptionUtil.decrypt(selected.getContactValueEncrypted()) : null;
                        builder.selectedContactType(selected.getContactType() != null ? selected.getContactType().name() : null)
                                .selectedContactValue(contactValue);
                    }

                    return builder.build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void resetTodayCards(UUID userId) {
        dailyCardRepository.deleteByUserIdAndDate(userId, LocalDate.now());
        log.info("오늘 카드 초기화 userId={}", userId);
    }

    // CardRefreshScheduler에서 유저별로 호출 — @Transactional이 Spring 프록시를 통해 적용됨
    @Transactional
    public List<DailyCard> generateAndSaveDailyCards(User user, LocalDate date) {
        return generateCardsInternal(user, date);
    }

    private List<DailyCard> generateCardsInternal(User user, LocalDate date) {
        if (user.getGender() == null) {
            return List.of();
        }

        // 오늘 이미 생성된 카드 수 확인 — 부족한 만큼만 추가
        int existingCount = dailyCardRepository.countByUserIdAndDate(user.getId(), date);
        int needed = DAILY_CARD_LIMIT - existingCount;
        if (needed <= 0) {
            return List.of();
        }

        Gender oppositeGender = user.getGender() == Gender.MALE ? Gender.FEMALE : Gender.MALE;

        // 서비스 오픈 이전 카드가 제외 풀에 포함되지 않도록 하한선 적용
        LocalDate thirtyDaysAgo = date.minusDays(30);
        LocalDate effectiveSince = thirtyDaysAgo.isBefore(SERVICE_LAUNCH_DATE)
                ? SERVICE_LAUNCH_DATE.minusDays(1)
                : thirtyDaysAgo;
        List<UUID> recentIds = dailyCardRepository.findRecentCandidateIds(
                user.getId(), effectiveSince);

        List<User> pool = recentIds.isEmpty()
                ? userRepository.findActiveByGender(oppositeGender)
                : userRepository.findActiveCandidates(oppositeGender, recentIds);

        // 학과 필터 (CLAUDE.md 16-4) — 풀 전체를 이미 로드하는 기존 구조라 인메모리 필터로 처리
        pool = applyDeptFilter(user, pool);

        if (pool.isEmpty()) {
            return List.of();
        }

        // 점수 기준: 상대방의 이상형 조건 vs 내 특징 — 내가 상대 이상형에 부합하는 정도
        List<UserTrait> myTraits = userTraitRepository.findByUserId(user.getId());

        List<ScoredCandidate> scored = pool.stream()
                .map(candidate -> {
                    List<IdealTrait> theirIdeals = idealTraitRepository.findByUserId(candidate.getId());
                    double score = MatchScoreUtil.calculateMatchScore(theirIdeals, myTraits, candidate.getBirthDate(), user.getBirthDate());
                    return new ScoredCandidate(candidate, score);
                })
                .collect(Collectors.toList());

        // 점수 높은 순 상위 30명 풀 확보 후 랜덤 셔플 — 30명 미만이면 전원 사용
        scored.sort(Comparator.comparingDouble(ScoredCandidate::score).reversed());
        int poolSize = Math.min(scored.size(), 30);
        List<ScoredCandidate> topPool = new ArrayList<>(scored.subList(0, poolSize));
        Collections.shuffle(topPool);

        List<DailyCard> cards = topPool.stream()
                .limit(needed)
                .map(sc -> DailyCard.builder()
                        .user(user)
                        .candidate(sc.user())
                        .matchScore(sc.score())
                        .cardDate(date)
                        .build())
                .collect(Collectors.toList());

        return dailyCardRepository.saveAll(cards);
    }

    // EXCLUDE_SAME은 상호 배제 — "같은 과와 서로 안 보이게".
    // 내가 걸든 상대가 걸든, 같은 과면 카드 풀에서 제외한다.
    // 단방향이면 필터를 건 유저가 같은 과 상대의 카드에 노출되어 선택·연락처 공개까지 이어질 수 있다 (기능 의도 위반).
    private List<User> applyDeptFilter(User user, List<User> pool) {
        String myDept = user.getVerifiedDepartment();
        // 내 학과 정보가 없으면(미인증 관리자 계정 등) "같은 과" 판정 자체가 불가 — 전체 유지
        if (myDept == null) {
            return pool;
        }
        boolean iExclude = user.getDeptFilterMode() == DeptFilterMode.EXCLUDE_SAME;
        return pool.stream()
                .filter(c -> {
                    // 상대 학과가 null(미인증)이면 "같은 과"가 아니므로 포함
                    if (!myDept.equals(c.getVerifiedDepartment())) {
                        return true;
                    }
                    boolean theyExclude = c.getDeptFilterMode() == DeptFilterMode.EXCLUDE_SAME;
                    return !iExclude && !theyExclude;
                })
                .collect(Collectors.toList());
    }

    /**
     * N+1 개선: 카드 목록 전체를 한 번에 조회해 DTO로 변환.
     * 기존: 카드 10장 × (사진 1쿼리 + 특징 1쿼리) = 22쿼리
     * 개선: 사진 IN 1쿼리 + 특징 IN 1쿼리 = 4쿼리 (기존 대비 81% 감소)
     */
    private List<MatchCardResponseDto> buildCardDtos(List<DailyCard> cards) {
        if (cards.isEmpty()) return List.of();

        List<UUID> candidateIds = cards.stream()
                .map(dc -> dc.getCandidate().getId())
                .collect(Collectors.toList());

        Map<UUID, String> photoUrlMap = buildPhotoUrlMap(candidateIds);
        Map<UUID, List<MatchCardResponseDto.TraitDto>> traitMap =
                buildTraitDtoMap(userTraitRepository.findByUserIdIn(candidateIds));

        return cards.stream()
                .map(dc -> {
                    User candidate = dc.getCandidate();
                    UUID cid = candidate.getId();
                    String birthYear = candidate.getBirthDate() != null
                            ? String.format("%02d년생", candidate.getBirthDate().getYear() % 100)
                            : null;
                    return MatchCardResponseDto.builder()
                            .candidateId(cid)
                            .nickname(candidate.getNickname())
                            .birthYear(birthYear)
                            .photoUrl(photoUrlMap.get(cid))
                            .visibleTraits(traitMap.getOrDefault(cid, List.of()))
                            .matchScore(dc.getMatchScore())
                            .university(candidate.getUniversity())
                            .build();
                })
                .collect(Collectors.toList());
    }

    // 여러 유저의 사진 URL을 Map<userId, url>로 일괄 조회
    private Map<UUID, String> buildPhotoUrlMap(List<UUID> userIds) {
        return photoRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(
                        p -> p.getUser().getId(),
                        p -> p.getThumbnailUrl() != null ? p.getThumbnailUrl() : p.getStorageUrl()
                ));
    }

    // 여러 유저의 공개 특징을 Map<userId, List<TraitDto>>로 일괄 변환
    private Map<UUID, List<MatchCardResponseDto.TraitDto>> buildTraitDtoMap(List<UserTrait> traits) {
        return traits.stream()
                .filter(UserTrait::isVisible)
                .collect(Collectors.groupingBy(
                        t -> t.getUser().getId(),
                        Collectors.mapping(
                                t -> MatchCardResponseDto.TraitDto.builder()
                                        .traitKey(t.getTraitKey().name())
                                        .traitValue(t.getTraitValue())
                                        .build(),
                                Collectors.toList()
                        )
                ));
    }

    // 단일 유저 전용 — respondToNote 등 1명만 필요한 경우에 사용 (N+1 아님)
    private List<MatchCardResponseDto.TraitDto> buildVisibleTraits(UUID userId) {
        return userTraitRepository.findByUserId(userId).stream()
                .filter(UserTrait::isVisible)
                .map(t -> MatchCardResponseDto.TraitDto.builder()
                        .traitKey(t.getTraitKey().name())
                        .traitValue(t.getTraitValue())
                        .build())
                .collect(Collectors.toList());
    }

    // 단일 유저 전용 — respondToNote 등 1명만 필요한 경우에 사용 (N+1 아님)
    private String getPhotoUrl(UUID userId) {
        return photoRepository.findByUserId(userId)
                .map(p -> p.getThumbnailUrl() != null ? p.getThumbnailUrl() : p.getStorageUrl())
                .orElse(null);
    }

    /**
     * 하루 선택 한도 = 기본 2 + 얼리버드(+1, 상시) + 리퍼럴 보너스(+1, rewarded_at이 오늘인 경우만).
     * 한도 계산은 반드시 이 메서드 하나를 공유한다 — select()와 getTodayCards()의 불일치 방지 (CLAUDE.md 16-5)
     */
    private int dailySelectLimit(User user) {
        int limit = BASE_DAILY_SELECT_LIMIT;
        if (user.isEarlyBird()) {
            limit++;
        }
        LocalDate today = LocalDate.now();
        if (referralEventRepository.hasRewardBetween(user.getId(),
                today.atStartOfDay(), today.plusDays(1).atStartOfDay())) {
            limit++;
        }
        return limit;
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    // 미인증 유저 진입 차단은 프론트 온보딩 UI만으로는 불충분 — API 직접 호출을 서버에서 막는다 (CLAUDE.md 16-1)
    private void requireStudentVerified(User user) {
        if (!user.isStudentVerified()) {
            throw new BusinessException(ErrorCode.STUDENT_VERIFICATION_REQUIRED);
        }
    }

    private record ScoredCandidate(User user, double score) {}
}
