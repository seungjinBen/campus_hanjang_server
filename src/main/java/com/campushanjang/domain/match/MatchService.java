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
import com.campushanjang.domain.user.IdealTraitRepository;
import com.campushanjang.domain.user.UserRepository;
import com.campushanjang.domain.user.UserTraitRepository;
import com.campushanjang.domain.user.entity.IdealTrait;
import com.campushanjang.domain.user.entity.User;
import com.campushanjang.domain.user.entity.UserTrait;
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

    private static final PolicyFactory SANITIZER = Sanitizers.FORMATTING;

    // 남녀 모두 하루 10장 — 카드 희소성 규칙
    private static final int DAILY_CARD_LIMIT = 10;
    // 하루 최대 선택 횟수 3회 — 선택 희소성 규칙
    private static final int DAILY_SELECT_LIMIT = 3;
    // 일치율 75% 기준 — 연락처 즉시 공개 vs 쪽지 분기 임계값
    private static final double CONTACT_REVEAL_THRESHOLD = 0.75;
    // 서비스 공식 오픈일 — 이전에 스케줄러가 생성한 카드가 30일 제외 풀에 포함되지 않도록 하한선으로 사용
    private static final LocalDate SERVICE_LAUNCH_DATE = LocalDate.of(2026, 5, 19);
    @Transactional
    public DailyCardsResponseDto getTodayCards(UUID userId) {
        User user = getUser(userId);
        LocalDate today = LocalDate.now();

        // 자정 스케줄러가 생성한 카드만 반환 — 온디맨드 생성 시 당일 가입자가 즉시 노출되는 문제 방지
        List<DailyCard> cards = dailyCardRepository.findByUserIdAndDate(userId, today);

        int remaining = Math.max(0, DAILY_SELECT_LIMIT - user.getDailySelectCount());

        List<MatchCardResponseDto> cardDtos = cards.stream()
                .map(this::buildCardDto)
                .collect(Collectors.toList());

        return DailyCardsResponseDto.builder()
                .cards(cardDtos)
                .remainingSelectCount(remaining)
                .build();
    }

    @Transactional
    public SelectResponseDto select(UUID userId, UUID candidateId) {
        User user = getUser(userId);

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

        // 하루 선택 횟수 3회 초과 방지
        if (user.getDailySelectCount() >= DAILY_SELECT_LIMIT) {
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
        User selected = userRepository.findById(selectedId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_NOT_FOUND));

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

        selectionRepository.save(Selection.builder()
                .selector(selector)
                .selected(selected)
                .type(SelectionType.NOTE_SENT)
                .build());

        log.info("쪽지 전송 selectorId={} selectedId={}", userId, selectedId);
    }

    @Transactional(readOnly = true)
    public List<ReceivedContactDto> getReceivedContacts(UUID userId) {
        return selectionRepository.findBySelectedIdAndType(userId, SelectionType.CONTACT_REVEALED).stream()
                .map(selection -> {
                    User selector = selection.getSelector();
                    return ReceivedContactDto.builder()
                            .selectorId(selector.getId())
                            .nickname(selector.getNickname())
                            .photoUrl(getPhotoUrl(selector.getId()))
                            .visibleTraits(buildVisibleTraits(selector.getId()))
                            .selectedAt(selection.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReceivedNoteDto> getReceivedNotes(UUID userId) {
        return noteRepository.findBySelectedIdWithSelector(userId).stream()
                .filter(note -> note.getStatus() != NoteStatus.REJECTED)
                .map(note -> {
                    User selector = note.getSelector();
                    ReceivedNoteDto.ReceivedNoteDtoBuilder builder = ReceivedNoteDto.builder()
                            .noteId(note.getId())
                            .status(note.getStatus().name())
                            .noteContent(note.getNoteContent())
                            .sentAt(note.getCreatedAt())
                            .respondedAt(note.getRespondedAt())
                            .selectorId(selector.getId())
                            .nickname(selector.getNickname())
                            .photoUrl(getPhotoUrl(selector.getId()))
                            .visibleTraits(buildVisibleTraits(selector.getId()));

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

    private MatchCardResponseDto buildCardDto(DailyCard dc) {
        User candidate = dc.getCandidate();
        return MatchCardResponseDto.builder()
                .candidateId(candidate.getId())
                .nickname(candidate.getNickname())
                .photoUrl(getPhotoUrl(candidate.getId()))
                .visibleTraits(buildVisibleTraits(candidate.getId()))
                .matchScore(dc.getMatchScore())
                .university(candidate.getUniversity())
                .build();
    }

    private List<MatchCardResponseDto.TraitDto> buildVisibleTraits(UUID userId) {
        return userTraitRepository.findByUserId(userId).stream()
                .filter(UserTrait::isVisible)
                .map(t -> MatchCardResponseDto.TraitDto.builder()
                        .traitKey(t.getTraitKey().name())
                        .traitValue(t.getTraitValue())
                        .build())
                .collect(Collectors.toList());
    }

    private String getPhotoUrl(UUID userId) {
        return photoRepository.findByUserId(userId)
                .map(p -> p.getThumbnailUrl() != null ? p.getThumbnailUrl() : p.getStorageUrl())
                .orElse(null);
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private record ScoredCandidate(User user, double score) {}
}
