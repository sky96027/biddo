package com.biddo.domain.report.entity;

import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.common.entity.BaseTimeEntity;
import com.biddo.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "report", indexes = {
        @Index(name = "idx_report_reported", columnList = "reported_id, status"),
        @Index(name = "idx_report_status", columnList = "status, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private Member reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_id", nullable = false)
    private Member reported;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id")
    private Auction auction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportReason reason;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @Column(columnDefinition = "TEXT")
    private String adminNote;

    @Builder
    public Report(Member reporter, Member reported, Auction auction,
                  ReportReason reason, String description) {
        this.reporter = reporter;
        this.reported = reported;
        this.auction = auction;
        this.reason = reason;
        this.description = description;
        this.status = ReportStatus.PENDING;
    }

    public void updateStatus(ReportStatus status, String adminNote) {
        this.status = status;
        this.adminNote = adminNote;
    }
}
