package com.biddo.api.admin.controller;

import com.biddo.api.common.response.ApiResponse;
import com.biddo.api.common.response.CursorResponse;
import com.biddo.api.report.dto.request.ReportStatusRequest;
import com.biddo.api.report.dto.response.ReportResponse;
import com.biddo.domain.report.entity.Report;
import com.biddo.domain.report.entity.ReportStatus;
import com.biddo.domain.report.service.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - 신고 관리")
@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private static final int DEFAULT_SIZE = 20;

    private final ReportService reportService;

    @GetMapping
    public ApiResponse<CursorResponse<ReportResponse>> getReports(
            @RequestParam(defaultValue = "PENDING") ReportStatus status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        int fetchSize = Math.min(size, DEFAULT_SIZE);
        List<Report> reports = reportService.findByStatus(status, cursor, fetchSize + 1);

        boolean hasNext = reports.size() > fetchSize;
        List<Report> content = hasNext ? reports.subList(0, fetchSize) : reports;
        List<ReportResponse> responses = content.stream().map(ReportResponse::from).toList();

        String nextCursor = hasNext ? String.valueOf(content.get(content.size() - 1).getId()) : null;

        return ApiResponse.success(CursorResponse.of(responses, nextCursor, hasNext));
    }

    @PatchMapping("/{reportId}")
    public ApiResponse<ReportResponse> updateReportStatus(
            @PathVariable Long reportId,
            @Valid @RequestBody ReportStatusRequest request) {
        Report report = reportService.updateStatus(reportId, request.getStatus(), request.getAdminNote());
        return ApiResponse.success(ReportResponse.from(report));
    }
}