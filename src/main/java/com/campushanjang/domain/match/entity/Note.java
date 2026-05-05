package com.campushanjang.domain.match.entity;

import com.campushanjang.domain.match.entity.enums.NoteStatus;
import com.campushanjang.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notes")
@Getter
@NoArgsConstructor
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selector_id", nullable = false)
    private User selector;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_id", nullable = false)
    private User selected;

    // 쪽지 내용 50자 제한 — CLAUDE.md 데이터 무결성 규칙
    @Column(name = "note_content", nullable = false, length = 50)
    private String noteContent;

    // 수신자가 응답하기 전까지 발신자 익명 보호
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NoteStatus status;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Note(User selector, User selected, String noteContent) {
        this.selector = selector;
        this.selected = selected;
        this.noteContent = noteContent;
        this.status = NoteStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    // 수락 시 양방향 연락처 공개 상태로 전환
    public void accept() {
        this.status = NoteStatus.ACCEPTED;
        this.respondedAt = LocalDateTime.now();
    }

    // 거절 시 발신자 익명 유지 (수신자는 누가 보냈는지 알 수 없음)
    public void reject() {
        this.status = NoteStatus.REJECTED;
        this.respondedAt = LocalDateTime.now();
    }
}
