package com.campushanjang.domain.match;

import com.campushanjang.common.annotation.RequiresMatchingEnabled;
import com.campushanjang.common.response.ApiResponse;
import com.campushanjang.config.UserRateLimit;
import com.campushanjang.domain.match.dto.*;
import com.campushanjang.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @GetMapping("/cards")
    @RequiresMatchingEnabled
    @UserRateLimit(operation = "match:cards:read")
    public ResponseEntity<ApiResponse<DailyCardsResponseDto>> getCards(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        DailyCardsResponseDto result = matchService.getTodayCards(principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/select")
    @RequiresMatchingEnabled
    @UserRateLimit(operation = "match:select")
    public ResponseEntity<ApiResponse<SelectResponseDto>> select(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SelectRequestDto request
    ) {
        SelectResponseDto result = matchService.select(principal.getId(), request.getCandidateId());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/note")
    @RequiresMatchingEnabled
    @UserRateLimit(operation = "match:note")
    public ResponseEntity<ApiResponse<Void>> sendNote(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody NoteRequestDto request
    ) {
        matchService.sendNote(principal.getId(), request.getSelectedId(), request.getContent());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/received/contacts")
    @RequiresMatchingEnabled
    @UserRateLimit(operation = "match:received")
    public ResponseEntity<ApiResponse<List<ReceivedContactDto>>> getReceivedContacts(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<ReceivedContactDto> contacts = matchService.getReceivedContacts(principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(contacts));
    }

    @GetMapping("/received/notes")
    @RequiresMatchingEnabled
    @UserRateLimit(operation = "match:received")
    public ResponseEntity<ApiResponse<List<ReceivedNoteDto>>> getReceivedNotes(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<ReceivedNoteDto> notes = matchService.getReceivedNotes(principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(notes));
    }

    @PostMapping("/note/{noteId}/respond")
    @RequiresMatchingEnabled
    @UserRateLimit(operation = "match:note:respond")
    public ResponseEntity<ApiResponse<NoteRespondResponseDto>> respondToNote(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID noteId,
            @Valid @RequestBody NoteRespondRequestDto request
    ) {
        NoteRespondResponseDto result = matchService.respondToNote(
                principal.getId(), noteId, request.getAction());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/sent/notes")
    @RequiresMatchingEnabled
    @UserRateLimit(operation = "match:received")
    public ResponseEntity<ApiResponse<List<SentNoteDto>>> getSentNotes(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<SentNoteDto> notes = matchService.getSentNotes(principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(notes));
    }
}
