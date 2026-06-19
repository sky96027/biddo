package com.biddo.domain.member.service;

import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.repository.MemberRepository;
import com.biddo.domain.report.repository.ReportRepository;
import com.biddo.domain.review.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TrustScoreCalculatorTest {

    @InjectMocks
    private TrustScoreCalculator trustScoreCalculator;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private MemberRepository memberRepository;

    private Member member;

    @BeforeEach
    void setUp() {
        member = Member.builder().email("test@test.com").password("encoded").nickname("tester").build();
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "createdAt", LocalDateTime.now().minusDays(180));
    }

    @Test
    @DisplayName("모든 요소 반영된 점수 계산")
    void calculate_allFactorsPresent_returnsWeightedScore() {
        given(reviewRepository.findAverageRatingByRevieweeId(1L)).willReturn(Optional.of(4.0));
        given(auctionRepository.countTotalEndedBySellerId(1L)).willReturn(10L);
        given(auctionRepository.countCompletedBySellerId(1L)).willReturn(8L);
        given(reportRepository.countResolvedByReportedId(1L)).willReturn(0L);

        BigDecimal score = trustScoreCalculator.calculate(member);

        // 리뷰: 4.0 * 0.5 = 2.0
        // 완료율: (8/10) * 5.0 * 0.25 = 1.0
        // 가입기간: (180/365) * 5.0 * 0.15 ≈ 0.37
        // 신고: 0
        // 합계 ≈ 3.4
        assertThat(score).isBetween(new BigDecimal("3.0"), new BigDecimal("3.8"));
    }

    @Test
    @DisplayName("리뷰 없는 신규 회원 — 가입 기간만 반영")
    void calculate_noReviewNewMember_returnsAccountAgeScoreOnly() {
        ReflectionTestUtils.setField(member, "createdAt", LocalDateTime.now().minusDays(30));
        given(reviewRepository.findAverageRatingByRevieweeId(1L)).willReturn(Optional.empty());
        given(auctionRepository.countTotalEndedBySellerId(1L)).willReturn(0L);
        given(reportRepository.countResolvedByReportedId(1L)).willReturn(0L);

        BigDecimal score = trustScoreCalculator.calculate(member);

        // 가입기간만: (30/365) * 5.0 * 0.15 ≈ 0.06
        assertThat(score).isBetween(new BigDecimal("0.0"), new BigDecimal("0.2"));
    }

    @Test
    @DisplayName("신고 이력 있으면 감점")
    void calculate_hasResolvedReports_returnsReducedScore() {
        given(reviewRepository.findAverageRatingByRevieweeId(1L)).willReturn(Optional.of(4.5));
        given(auctionRepository.countTotalEndedBySellerId(1L)).willReturn(10L);
        given(auctionRepository.countCompletedBySellerId(1L)).willReturn(10L);
        given(reportRepository.countResolvedByReportedId(1L)).willReturn(2L);

        BigDecimal score = trustScoreCalculator.calculate(member);

        // 리뷰: 4.5 * 0.5 = 2.25
        // 완료율: (10/10) * 5.0 * 0.25 = 1.25
        // 가입기간: (180/365) * 5.0 * 0.15 ≈ 0.37
        // 신고: (2 * 0.5) * 0.1 = 0.1
        // 합계 ≈ 3.77
        assertThat(score).isBetween(new BigDecimal("3.4"), new BigDecimal("4.2"));
    }

    @Test
    @DisplayName("점수가 0 미만이면 0으로 클램핑")
    void calculate_scoreBelowZero_returnsZero() {
        given(reviewRepository.findAverageRatingByRevieweeId(1L)).willReturn(Optional.of(0.0));
        given(auctionRepository.countTotalEndedBySellerId(1L)).willReturn(0L);
        given(reportRepository.countResolvedByReportedId(1L)).willReturn(20L);

        BigDecimal score = trustScoreCalculator.calculate(member);

        assertThat(score).isEqualByComparingTo(BigDecimal.ZERO.setScale(1));
    }

    @Test
    @DisplayName("점수가 5.0 초과이면 5.0으로 클램핑")
    void calculate_scoreExceedsMax_returnsFive() {
        ReflectionTestUtils.setField(member, "createdAt", LocalDateTime.now().minusDays(500));
        given(reviewRepository.findAverageRatingByRevieweeId(1L)).willReturn(Optional.of(5.0));
        given(auctionRepository.countTotalEndedBySellerId(1L)).willReturn(100L);
        given(auctionRepository.countCompletedBySellerId(1L)).willReturn(100L);
        given(reportRepository.countResolvedByReportedId(1L)).willReturn(0L);

        BigDecimal score = trustScoreCalculator.calculate(member);

        assertThat(score).isLessThanOrEqualTo(new BigDecimal("5.0"));
    }

    @Test
    @DisplayName("recalculateAll — 전체 회원 점수 재계산")
    void recalculateAll_membersExist_updatesAllScores() {
        given(memberRepository.findAll()).willReturn(List.of(member));
        given(reviewRepository.findAverageRatingByRevieweeId(1L)).willReturn(Optional.of(3.0));
        given(auctionRepository.countTotalEndedBySellerId(1L)).willReturn(5L);
        given(auctionRepository.countCompletedBySellerId(1L)).willReturn(4L);
        given(reportRepository.countResolvedByReportedId(1L)).willReturn(0L);

        trustScoreCalculator.recalculateAll();

        assertThat(member.getTrustScore()).isNotNull();
        assertThat(member.getTrustScore().doubleValue()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("거래 완료율 100% — 만점 반영")
    void calculate_perfectCompletionRate_returnsHighScore() {
        given(reviewRepository.findAverageRatingByRevieweeId(1L)).willReturn(Optional.of(5.0));
        given(auctionRepository.countTotalEndedBySellerId(1L)).willReturn(20L);
        given(auctionRepository.countCompletedBySellerId(1L)).willReturn(20L);
        given(reportRepository.countResolvedByReportedId(1L)).willReturn(0L);

        BigDecimal score = trustScoreCalculator.calculate(member);

        // 리뷰: 5.0 * 0.5 = 2.5
        // 완료율: 1.0 * 5.0 * 0.25 = 1.25
        // 가입기간 ≈ 0.37
        // 합계 ≈ 4.12
        assertThat(score.doubleValue()).isGreaterThan(3.5);
    }
}