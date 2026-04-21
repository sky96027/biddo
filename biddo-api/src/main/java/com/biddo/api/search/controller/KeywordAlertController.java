package com.biddo.api.search.controller;

import com.biddo.api.common.response.ApiResponse;
import com.biddo.api.common.security.CustomUserDetails;
import com.biddo.api.search.dto.request.KeywordAlertRequest;
import com.biddo.api.search.dto.request.KeywordAlertUpdateRequest;
import com.biddo.api.search.dto.response.KeywordAlertResponse;
import com.biddo.domain.search.entity.KeywordAlert;
import com.biddo.domain.search.service.KeywordAlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/keyword-alerts")
@RequiredArgsConstructor
public class KeywordAlertController {

    private final KeywordAlertService keywordAlertService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KeywordAlertResponse> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody KeywordAlertRequest request) {
        KeywordAlert alert = keywordAlertService.create(
                userDetails.getMemberId(),
                request.getKeyword(),
                request.getCategoryId(),
                request.getMaxPrice()
        );
        return ApiResponse.success(KeywordAlertResponse.from(alert));
    }

    @GetMapping
    public ApiResponse<List<KeywordAlertResponse>> getMyAlerts(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<KeywordAlertResponse> responses = keywordAlertService.findByMemberId(userDetails.getMemberId())
                .stream()
                .map(KeywordAlertResponse::from)
                .toList();
        return ApiResponse.success(responses);
    }

    @PutMapping("/{alertId}")
    public ApiResponse<KeywordAlertResponse> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long alertId,
            @RequestBody KeywordAlertUpdateRequest request) {
        KeywordAlert alert = keywordAlertService.update(
                alertId,
                userDetails.getMemberId(),
                request.getCategoryId(),
                request.getMaxPrice()
        );
        return ApiResponse.success(KeywordAlertResponse.from(alert));
    }

    @PatchMapping("/{alertId}/toggle")
    public ApiResponse<Void> toggleActive(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long alertId) {
        keywordAlertService.toggleActive(alertId, userDetails.getMemberId());
        return ApiResponse.success();
    }

    @DeleteMapping("/{alertId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long alertId) {
        keywordAlertService.delete(alertId, userDetails.getMemberId());
        return ApiResponse.success();
    }
}