package com.biddo.api.report.dto.request;

import com.biddo.domain.report.entity.ReportReason;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReportCreateRequest {

    @NotNull(message = "피신고자 ID는 필수입니다.")
    private Long reportedId;

    private Long auctionId;

    @NotNull(message = "신고 사유는 필수입니다.")
    private ReportReason reason;

    private String description;
}