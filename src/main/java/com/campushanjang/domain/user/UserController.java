package com.campushanjang.domain.user;

import com.campushanjang.common.response.ApiResponse;
import com.campushanjang.domain.user.dto.*;
import com.campushanjang.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UserProfileRequestDto request
    ) {
        userService.updateProfile(principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponseDto>> getMyProfile(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UserProfileResponseDto profile = userService.getMyProfile(principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(profile));
    }

    @PutMapping("/traits")
    public ResponseEntity<ApiResponse<Void>> updateTraits(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody TraitsUpdateRequestDto request
    ) {
        userService.updateTraits(principal.getId(), request.getTraits());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PutMapping("/ideal")
    public ResponseEntity<ApiResponse<Void>> updateIdealTraits(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody IdealUpdateRequestDto request
    ) {
        userService.updateIdealTraits(principal.getId(), request.getIdeals());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/profile-complete")
    public ResponseEntity<ApiResponse<UserService.ProfileCompleteResponseDto>> checkProfileComplete(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UserService.ProfileCompleteResponseDto result = userService.checkProfileComplete(principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        userService.deleteAccount(principal.getId());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
