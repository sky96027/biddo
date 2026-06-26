package com.biddo.domain.report.service;

import com.biddo.domain.auction.service.AuctionService;
import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.service.MemberService;
import com.biddo.domain.report.entity.Report;
import com.biddo.domain.report.entity.ReportReason;
import com.biddo.domain.report.entity.ReportStatus;
import com.biddo.domain.report.exception.ReportErrorCode;
import com.biddo.domain.report.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @InjectMocks
    private ReportService reportService;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private MemberService memberService;

    @Mock
    private AuctionService auctionService;

    private Member reporter;
    private Member reported;

    @BeforeEach
    void setUp() {
        reporter = Member.builder().email("reporter@test.com").password("encoded").nickname("reporter").build();
        setId(reporter, 1L);

        reported = Member.builder().email("reported@test.com").password("encoded").nickname("reported").build();
        setId(reported, 2L);
    }

    @Test
    @DisplayName("신고 접수 성공")
    void create_validInput_returnsSavedReport() {
        given(memberService.findById(1L)).willReturn(reporter);
        given(memberService.findById(2L)).willReturn(reported);
        given(reportRepository.save(any(Report.class))).willAnswer(inv -> {
            Report r = inv.getArgument(0);
            setId(r, 1L);
            return r;
        });

        Report result = reportService.create(1L, 2L, null, ReportReason.FRAUD, "사기 의심");

        assertThat(result.getReason()).isEqualTo(ReportReason.FRAUD);
        assertThat(result.getStatus()).isEqualTo(ReportStatus.PENDING);
        assertThat(result.getDescription()).isEqualTo("사기 의심");
    }

    @Test
    @DisplayName("신고 실패 - 본인 신고")
    void create_selfReport_throwsException() {
        assertThatThrownBy(() -> reportService.create(1L, 1L, null, ReportReason.FRAUD, "설명"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ReportErrorCode.SELF_REPORT_NOT_ALLOWED));
    }

    @Test
    @DisplayName("신고 상태 변경 성공 - PENDING → REVIEWED")
    void updateStatus_pendingToReviewed_returnsUpdatedReport() {
        Report report = Report.builder()
                .reporter(reporter).reported(reported)
                .reason(ReportReason.NO_TRADE).description("거래 거부")
                .build();
        setId(report, 1L);

        given(reportRepository.findById(1L)).willReturn(Optional.of(report));

        Report result = reportService.updateStatus(1L, ReportStatus.REVIEWED, "검토 중");

        assertThat(result.getStatus()).isEqualTo(ReportStatus.REVIEWED);
        assertThat(result.getAdminNote()).isEqualTo("검토 중");
    }

    @Test
    @DisplayName("신고 상태 변경 성공 - REVIEWED → RESOLVED")
    void updateStatus_reviewedToResolved_returnsUpdatedReport() {
        Report report = Report.builder()
                .reporter(reporter).reported(reported)
                .reason(ReportReason.NO_TRADE).description("거래 거부")
                .build();
        setId(report, 1L);
        report.updateStatus(ReportStatus.REVIEWED, "검토 중");

        given(reportRepository.findById(1L)).willReturn(Optional.of(report));

        Report result = reportService.updateStatus(1L, ReportStatus.RESOLVED, "처리 완료");

        assertThat(result.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(result.getAdminNote()).isEqualTo("처리 완료");
    }

    @Test
    @DisplayName("신고 상태 변경 실패 - 유효하지 않은 전이 (PENDING → RESOLVED)")
    void updateStatus_invalidTransition_throwsException() {
        Report report = Report.builder()
                .reporter(reporter).reported(reported)
                .reason(ReportReason.NO_TRADE).description("거래 거부")
                .build();
        setId(report, 1L);

        given(reportRepository.findById(1L)).willReturn(Optional.of(report));

        assertThatThrownBy(() -> reportService.updateStatus(1L, ReportStatus.RESOLVED, "처리 완료"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ReportErrorCode.INVALID_STATUS_TRANSITION));
    }

    @Test
    @DisplayName("신고 상태 변경 실패 - 최종 상태에서 전이 불가 (RESOLVED → PENDING)")
    void updateStatus_fromFinalStatus_throwsException() {
        Report report = Report.builder()
                .reporter(reporter).reported(reported)
                .reason(ReportReason.NO_TRADE).description("거래 거부")
                .build();
        setId(report, 1L);
        report.updateStatus(ReportStatus.REVIEWED, "검토 중");
        report.updateStatus(ReportStatus.RESOLVED, "처리 완료");

        given(reportRepository.findById(1L)).willReturn(Optional.of(report));

        assertThatThrownBy(() -> reportService.updateStatus(1L, ReportStatus.PENDING, "재검토"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ReportErrorCode.INVALID_STATUS_TRANSITION));
    }

    @Test
    @DisplayName("신고 상태 변경 실패 - 신고 없음")
    void updateStatus_notFound_throwsException() {
        given(reportRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.updateStatus(999L, ReportStatus.RESOLVED, "메모"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_NOT_FOUND));
    }

    private void setId(Object entity, Long id) {
        try {
            java.lang.reflect.Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}