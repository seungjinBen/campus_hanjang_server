package com.campushanjang.domain.admin;

import com.campushanjang.common.response.ApiResponse;
import com.campushanjang.domain.admin.dto.AdminCreateUserRequestDto;
import com.campushanjang.domain.admin.dto.AdminCreateUserResponseDto;
import com.campushanjang.domain.match.MatchService;
import com.campushanjang.domain.photo.dto.PhotoUploadResponseDto;
import com.campushanjang.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final MatchService matchService;

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<AdminCreateUserResponseDto>> createUser(
            @Valid @RequestBody AdminCreateUserRequestDto request
    ) {
        AdminCreateUserResponseDto response = adminService.createUser(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/match/cards/today")
    public ResponseEntity<ApiResponse<Void>> resetMyDailyCards(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        matchService.resetTodayCards(principal.getId());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping(value = "/users/{userId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PhotoUploadResponseDto>> uploadPhotoForUser(
            @PathVariable UUID userId,
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        PhotoUploadResponseDto response = adminService.uploadPhotoForUser(userId, file);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
