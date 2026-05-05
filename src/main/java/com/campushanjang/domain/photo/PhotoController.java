package com.campushanjang.domain.photo;

import com.campushanjang.common.response.ApiResponse;
import com.campushanjang.config.UserRateLimit;
import com.campushanjang.domain.photo.dto.PhotoUploadResponseDto;
import com.campushanjang.domain.photo.dto.ThumbnailUpdateRequestDto;
import com.campushanjang.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PhotoUploadResponseDto>> getMyPhoto(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PhotoUploadResponseDto result = photoService.getMyPhoto(principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @UserRateLimit(operation = "photo:upload")
    public ResponseEntity<ApiResponse<PhotoUploadResponseDto>> upload(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        PhotoUploadResponseDto result = photoService.upload(principal.getId(), file);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PatchMapping("/thumbnail")
    public ResponseEntity<ApiResponse<Void>> updateThumbnail(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ThumbnailUpdateRequestDto request
    ) {
        photoService.updateThumbnail(principal.getId(), request.getThumbnailUrl());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
