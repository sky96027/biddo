package com.biddo.api.report.dto.response;

import com.biddo.domain.report.entity.Report;
import com.biddo.domain.report.entity.ReportReason;
import com.biddo.domain.report.entity.ReportStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ReportResponse {

    private final Long reportId;
    private final String reportedNickname;
    private final Long auctionId;
    private final ReportReason reason;
    private final String description;
    private final ReportStatus status;
    private final LocalDateTime createdAt;

    private ReportResponse(Long reportId, String reportedNickname, Long auctionId,
                           ReportReason reason, String description,
                           ReportStatus status, LocalDateTime createdAt) {
        this.reportId = reportId;
        this.reportedNickname = reportedNickname;
        this.auctionId = auctionId;
        this.reason = reason;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static ReportResponse from(Report report) {
        return new ReportResponse(
                report.getId(),
                report.getReported().getNickname(),
                report.getAuction() != null ? report.getAuction().getId() : null,
                report.getReason(),
                report.getDescription(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }
}