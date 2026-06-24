package com.biddo.domain.bid.service;

import com.biddo.domain.auction.entity.Auction;
import com.biddo.domain.auction.entity.AuctionStatus;
import com.biddo.domain.auction.port.out.AuctionLifecyclePort;
import com.biddo.domain.auction.service.AuctionService;
import com.biddo.domain.bid.exception.BidErrorCode;
import com.biddo.domain.bid.entity.AutoBid;
import com.biddo.domain.bid.entity.Bid;
import com.biddo.domain.bid.entity.BidType;
import com.biddo.domain.bid.port.out.AuctionLockPort;
import com.biddo.domain.bid.port.out.AutoBidRepository;
import com.biddo.domain.bid.port.out.BidEventPublisher;
import com.biddo.domain.bid.port.out.BidRepository;
import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidService {

    private static final int MAX_AUTO_BID_CHAIN = 10;
    private static final int SNIPING_MINUTES = 10;

    private final BidRepository bidRepository;
    private final AutoBidRepository autoBidRepository;
    private final BidEventPublisher bidEventPublisher;
    private final AuctionLifecyclePort auctionLifecyclePort;
    private final AuctionService auctionService;
    private final MemberService memberService;
    private final AuctionLockPort auctionLockPort;
    private final TransactionTemplate transactionTemplate;

    public Bid placeBid(Long auctionId, Long bidderId, Long bidAmount) {
        Bid[] result = new Bid[1];
        auctionLockPort.executeWithLock(auctionId, () ->
                result[0] = transactionTemplate.execute(status ->
                        placeBidInternal(auctionId, bidderId, bidAmount))
        );
        return result[0];
    }

    public Bid buyNow(Long auctionId, Long bidderId) {
        Bid[] result = new Bid[1];
        auctionLockPort.executeWithLock(auctionId, () ->
                result[0] = transactionTemplate.execute(status ->
                        buyNowInternal(auctionId, bidderId))
        );
        return result[0];
    }

    public AutoBid setAutoBid(Long auctionId, Long bidderId, Long maxAmount) {
        AutoBid[] result = new AutoBid[1];
        auctionLockPort.executeWithLock(auctionId, () ->
                result[0] = transactionTemplate.execute(status ->
                        setAutoBidInternal(auctionId, bidderId, maxAmount))
        );
        return result[0];
    }

    private Bid placeBidInternal(Long auctionId, Long bidderId, Long bidAmount) {
        Auction auction = auctionService.findAuctionById(auctionId);
        Member bidder = memberService.findById(bidderId);

        validateActive(auction);
        validateNotSeller(auction, bidderId);
        validateBidAmount(auction, bidAmount);

        Bid bid = createBid(auction, bidder, bidAmount, BidType.MANUAL);
        extendIfSniping(auction);
        bidEventPublisher.publishBidPlaced(bid);
        log.info("입찰 성공: auctionId={}, bidderId={}, amount={}, currentPrice={}",
                auctionId, bidderId, bidAmount, auction.getCurrentPrice());

        processAutoBids(auction, bidderId);

        return bid;
    }

    private Bid buyNowInternal(Long auctionId, Long bidderId) {
        Auction auction = auctionService.findAuctionById(auctionId);
        Member bidder = memberService.findById(bidderId);

        validateActive(auction);
        validateNotSeller(auction, bidderId);

        if (auction.getBuyNowPrice() == null) {
            throw new BusinessException(BidErrorCode.BUY_NOW_NOT_AVAILABLE);
        }

        Bid bid = createBid(auction, bidder, auction.getBuyNowPrice(), BidType.BUY_NOW);
        auctionService.completeBuyNow(auction, bidder);
        bidEventPublisher.publishBidPlaced(bid);

        return bid;
    }

    private AutoBid setAutoBidInternal(Long auctionId, Long bidderId, Long maxAmount) {
        Auction auction = auctionService.findAuctionById(auctionId);
        Member bidder = memberService.findById(bidderId);

        validateActive(auction);
        validateNotSeller(auction, bidderId);

        long minimumBid = auction.getCurrentPrice() + calculateMinIncrement(auction.getCurrentPrice());
        if (maxAmount < minimumBid) {
            throw new BusinessException(BidErrorCode.AUTO_BID_MAX_TOO_LOW);
        }

        AutoBid autoBid = autoBidRepository.findByAuctionIdAndBidderId(auctionId, bidderId)
                .map(existing -> {
                    existing.updateMaxAmount(maxAmount);
                    return existing;
                })
                .orElseGet(() -> AutoBid.builder()
                        .auction(auction)
                        .bidder(bidder)
                        .maxAmount(maxAmount)
                        .build());

        return autoBidRepository.save(autoBid);
    }

    @Transactional
    public void cancelAutoBid(Long auctionId, Long bidderId) {
        AutoBid autoBid = autoBidRepository.findByAuctionIdAndBidderId(auctionId, bidderId)
                .orElseThrow(() -> new BusinessException(BidErrorCode.AUTO_BID_NOT_FOUND));
        autoBid.deactivate();
    }

    @Transactional(readOnly = true)
    public List<Bid> getBidHistory(Long auctionId, Long cursor, int size) {
        return bidRepository.findBidHistory(auctionId, cursor, size);
    }

    public static long calculateMinIncrement(long currentPrice) {
        double rate;
        if (currentPrice < 10_000) {
            rate = 0.10;
        } else if (currentPrice < 100_000) {
            rate = 0.05;
        } else if (currentPrice < 1_000_000) {
            rate = 0.03;
        } else {
            rate = 0.01;
        }
        long increment = (long) (currentPrice * rate);
        return roundUpTo100(increment);
    }

    private static long roundUpTo100(long amount) {
        return ((amount + 99) / 100) * 100;
    }

    private Bid createBid(Auction auction, Member bidder, Long bidAmount, BidType bidType) {
        bidRepository.clearWinningBid(auction.getId());

        Bid bid = Bid.builder()
                .auction(auction)
                .bidder(bidder)
                .bidAmount(bidAmount)
                .bidType(bidType)
                .build();
        bid = bidRepository.save(bid);

        auctionService.applyBid(auction, bidAmount, bidder);

        return bid;
    }

    private void processAutoBids(Auction auction, Long triggerBidderId) {
        int chainCount = 0;
        for (int i = 0; i < MAX_AUTO_BID_CHAIN; i++) {
            List<AutoBid> activeAutoBids = autoBidRepository
                    .findActiveByAuctionIdExcludingBidder(auction.getId(), triggerBidderId);

            if (activeAutoBids.isEmpty()) {
                break;
            }

            AutoBid bestAutoBid = activeAutoBids.stream()
                    .sorted(Comparator.comparing(AutoBid::getMaxAmount).reversed()
                            .thenComparing(AutoBid::getCreatedAt))
                    .findFirst()
                    .orElse(null);

            if (bestAutoBid == null) {
                break;
            }

            long minBid = auction.getCurrentPrice() + calculateMinIncrement(auction.getCurrentPrice());

            if (bestAutoBid.getMaxAmount() < minBid) {
                bestAutoBid.deactivate();
                break;
            }

            long autoBidAmount = Math.min(minBid, bestAutoBid.getMaxAmount());
            Bid autoBid = createBid(auction, bestAutoBid.getBidder(), autoBidAmount, BidType.AUTO);
            extendIfSniping(auction);
            bidEventPublisher.publishBidPlaced(autoBid);
            chainCount++;

            if (bestAutoBid.getMaxAmount() <= autoBidAmount + calculateMinIncrement(autoBidAmount)) {
                bestAutoBid.deactivate();
            }

            triggerBidderId = bestAutoBid.getBidder().getId();
        }
        if (chainCount > 0) {
            log.debug("자동입찰 연쇄 처리: auctionId={}, chainCount={}, finalPrice={}",
                    auction.getId(), chainCount, auction.getCurrentPrice());
        }
    }

    private void extendIfSniping(Auction auction) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime snipingThreshold = auction.getEndTime().minusMinutes(SNIPING_MINUTES);
        if (now.isAfter(snipingThreshold)) {
            LocalDateTime newEndTime = now.plusMinutes(SNIPING_MINUTES);
            auction.extendEndTime(newEndTime);
            auctionLifecyclePort.scheduleEnd(auction.getId(), newEndTime);
        }
    }

    private void validateActive(Auction auction) {
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new BusinessException(BidErrorCode.AUCTION_NOT_ACTIVE);
        }
    }

    private void validateNotSeller(Auction auction, Long bidderId) {
        if (auction.isSeller(bidderId)) {
            throw new BusinessException(BidErrorCode.SELF_BID_NOT_ALLOWED);
        }
    }

    private void validateBidAmount(Auction auction, Long bidAmount) {
        long minimumBid = auction.getCurrentPrice() + calculateMinIncrement(auction.getCurrentPrice());
        if (bidAmount < minimumBid) {
            throw new BusinessException(BidErrorCode.BID_AMOUNT_TOO_LOW);
        }
    }
}