package com.biddo.domain.member.service;

import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.repository.MemberRepository;
import com.biddo.domain.report.service.ReportService;
import com.biddo.domain.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrustScoreCalculator {

    private static final double WEIGHT_REVIEW_AVG = 0.5;
    private static final double WEIGHT_COMPLETION_RATE = 0.25;
    private static final double WEIGHT_ACCOUNT_AGE = 0.15;
    private static final double WEIGHT_REPORT_PENALTY = 0.1;

    private static final double MAX_SCORE = 5.0;
    private static final long ACCOUNT_AGE_MAX_DAYS = 365;
    private static final double REPORT_PENALTY_PER_COUNT = 0.5;

    private final ReviewService reviewService;
    private final AuctionRepository auctionRepository;
    private final ReportService reportService;
    private final MemberRepository memberRepository;

    @Transactional
    public void recalculateAll() {
        List<Member> members = memberRepository.findAll();
        int updated = 0;

        for (Member member : members) {
            BigDecimal score = calculate(member);
            member.updateTrustScore(score);
            updated++;
        }

        log.info("Trust score recalculated: {} members updated", updated);
    }

    public BigDecimal calculate(Member member) {
        double reviewScore = calculateReviewScore(member.getId());
        double completionScore = calculateCompletionRate(member.getId());
        double accountAgeScore = calculateAccountAgeScore(member.getCreatedAt());
        double reportPenalty = calculateReportPenalty(member.getId());

        double raw = (reviewScore * WEIGHT_REVIEW_AVG)
                + (completionScore * WEIGHT_COMPLETION_RATE)
                + (accountAgeScore * WEIGHT_ACCOUNT_AGE)
                - (reportPenalty * WEIGHT_REPORT_PENALTY);

        double clamped = Math.max(0.0, Math.min(MAX_SCORE, raw));
        return BigDecimal.valueOf(clamped).setScale(1, RoundingMode.HALF_UP);
    }

    private double calculateReviewScore(Long memberId) {
        return reviewService.getAverageRating(memberId);
    }

    private double calculateCompletionRate(Long memberId) {
        long totalEnded = auctionRepository.countTotalEndedBySellerId(memberId);
        if (totalEnded == 0) return 0.0;

        long completed = auctionRepository.countCompletedBySellerId(memberId);
        double rate = (double) completed / totalEnded;
        return rate * MAX_SCORE;
    }

    private double calculateAccountAgeScore(LocalDateTime createdAt) {
        if (createdAt == null) return 0.0;
        long days = ChronoUnit.DAYS.between(createdAt, LocalDateTime.now());
        double ratio = Math.min(1.0, (double) days / ACCOUNT_AGE_MAX_DAYS);
        return ratio * MAX_SCORE;
    }

    private double calculateReportPenalty(Long memberId) {
        long reportCount = reportService.countResolvedByReportedId(memberId);
        return Math.min(MAX_SCORE, reportCount * REPORT_PENALTY_PER_COUNT);
    }
}