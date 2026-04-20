package com.biddo.api.report.dto.request;

import com.biddo.domain.report.entity.ReportStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReportStatusRequest {

    @NotNull(message = "처리 상태는 필수입니다.")
    private ReportStatus status;

    private String adminNote;
}