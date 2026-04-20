package com.biddo.api.report.controller;

import com.biddo.api.common.response.ApiResponse;
import com.biddo.api.common.response.CursorResponse;
import com.biddo.api.common.security.CustomUserDetails;
import com.biddo.api.report.dto.request.ReportCreateRequest;
import com.biddo.api.report.dto.response.ReportResponse;
import com.biddo.domain.report.entity.Report;
import com.biddo.domain.report.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private static final int DEFAULT_SIZE = 20;

    private final ReportService reportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportResponse> createReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ReportCreateRequest request) {
        Report report = reportService.create(
                userDetails.getMemberId(),
                request.getReportedId(),
                request.getAuctionId(),
                request.getReason(),
                request.getDescription());
        return ApiResponse.success(ReportResponse.from(report));
    }

    @GetMapping("/me")
    public ApiResponse<CursorResponse<ReportResponse>> getMyReports(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        int fetchSize = Math.min(size, DEFAULT_SIZE);
        List<Report> reports = reportService.findMyReports(userDetails.getMemberId(), cursor, fetchSize + 1);

        boolean hasNext = reports.size() > fetchSize;
        List<Report> content = hasNext ? reports.subList(0, fetchSize) : reports;
        List<ReportResponse> responses = content.stream().map(ReportResponse::from).toList();

        String nextCursor = hasNext ? String.valueOf(content.get(content.size() - 1).getId()) : null;

        return ApiResponse.success(CursorResponse.of(responses, nextCursor, hasNext));
    }
}