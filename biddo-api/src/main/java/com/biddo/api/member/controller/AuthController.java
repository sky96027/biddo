package com.biddo.api.member.controller;

import com.biddo.api.common.response.ApiResponse;
import com.biddo.api.common.security.CustomUserDetails;
import com.biddo.api.member.dto.request.LoginRequest;
import com.biddo.api.member.dto.request.PasswordChangeRequest;
import com.biddo.api.member.dto.request.RefreshRequest;
import com.biddo.api.member.dto.request.SignupRequest;
import com.biddo.api.member.dto.response.SignupResponse;
import com.biddo.api.member.dto.response.TokenResponse;
import com.biddo.api.member.service.AuthService;
import com.biddo.domain.member.entity.Member;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        Member member = authService.signup(request.getEmail(), request.getPassword(), request.getNickname());
        return ApiResponse.success(new SignupResponse(member));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse tokenResponse = authService.login(request.getEmail(), request.getPassword());
        return ApiResponse.success(tokenResponse);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> logout(@AuthenticationPrincipal CustomUserDetails userDetails) {
        authService.logout(userDetails.getMemberId());
        return ApiResponse.success();
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenResponse tokenResponse = authService.refresh(request.getRefreshToken());
        return ApiResponse.success(tokenResponse);
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal CustomUserDetails userDetails,
                                            @Valid @RequestBody PasswordChangeRequest request) {
        authService.changePassword(userDetails.getMemberId(), request.getCurrentPassword(), request.getNewPassword());
        return ApiResponse.success();
    }
}