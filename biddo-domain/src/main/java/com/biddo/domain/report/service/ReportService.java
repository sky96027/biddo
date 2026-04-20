package com.biddo.domain.report.service;

import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.exception.MemberErrorCode;
import com.biddo.domain.member.repository.MemberRepository;
import com.biddo.domain.report.entity.Report;
import com.biddo.domain.report.entity.ReportReason;
import com.biddo.domain.report.entity.ReportStatus;
import com.biddo.domain.report.exception.ReportErrorCode;
import com.biddo.domain.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reportRepository;
    private final MemberRepository memberRepository;
    private final AuctionRepository auctionRepository;

    @Transactional
    public Report create(Long reporterId, Long reportedId, Long auctionId,
                         ReportReason reason, String description) {
        if (reporterId.equals(reportedId)) {
            throw new BusinessException(ReportErrorCode.SELF_REPORT_NOT_ALLOWED);
        }

        Member reporter = memberRepository.findById(reporterId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        Member reported = memberRepository.findById(reportedId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));

        Auction auction = null;
        if (auctionId != null) {
            auction = auctionRepository.findById(auctionId).orElse(null);
        }

        Report report = Report.builder()
                .reporter(reporter)
                .reported(reported)
                .auction(auction)
                .reason(reason)
                .description(description)
                .build();

        return reportRepository.save(report);
    }

    public List<Report> findMyReports(Long reporterId, Long cursor, int size) {
        PageRequest pageRequest = PageRequest.of(0, size);
        if (cursor == null) {
            return reportRepository.findByReporterIdFirstPage(reporterId, pageRequest);
        }
        return reportRepository.findByReporterIdWithCursor(reporterId, cursor, pageRequest);
    }

    public List<Report> findByStatus(ReportStatus status, Long cursor, int size) {
        PageRequest pageRequest = PageRequest.of(0, size);
        if (cursor == null) {
            return reportRepository.findByStatusFirstPage(status, pageRequest);
        }
        return reportRepository.findByStatusWithCursor(status, cursor, pageRequest);
    }

    @Transactional
    public Report updateStatus(Long reportId, ReportStatus status, String adminNote) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ReportErrorCode.REPORT_NOT_FOUND));
        report.updateStatus(status, adminNote);
        return report;
    }
}