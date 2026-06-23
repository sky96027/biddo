package com.biddo.domain.auction.service;

import com.biddo.domain.auction.exception.AuctionErrorCode;
import com.biddo.domain.auction.exception.AuctionNotFoundException;
import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.auction.model.AuctionImage;
import com.biddo.domain.auction.model.AuctionStatus;
import com.biddo.domain.auction.model.ItemCondition;
import com.biddo.domain.auction.port.out.AuctionEventPublisher;
import com.biddo.domain.auction.port.out.AuctionLifecyclePort;
import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.bid.port.out.AutoBidRepository;
import com.biddo.domain.chat.service.ChatService;
import com.biddo.domain.category.entity.Category;
import com.biddo.domain.category.repository.CategoryRepository;
import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.exception.MemberErrorCode;
import com.biddo.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final MemberRepository memberRepository;
    private final CategoryRepository categoryRepository;
    private final AuctionEventPublisher auctionEventPublisher;
    private final AuctionLifecyclePort auctionLifecyclePort;
    private final AutoBidRepository autoBidRepository;
    private final ChatService chatService;

    @Transactional
    public Auction create(Long sellerId, Long categoryId, String title, String description,
                          ItemCondition condition, Long startingPrice, Long buyNowPrice,
                          LocalDateTime startTime, LocalDateTime endTime, List<String> imageUrls) {
        Member seller = memberRepository.findById(sellerId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(AuctionErrorCode.CATEGORY_NOT_FOUND));

        LocalDateTime effectiveStartTime = (startTime != null) ? startTime : LocalDateTime.now();
        validateAuctionTime(effectiveStartTime, endTime);
        validateAuctionDuration(effectiveStartTime, endTime);

        Auction auction = Auction.builder()
                .seller(seller)
                .category(category)
                .title(title)
                .description(description)
                .condition(condition)
                .startingPrice(startingPrice)
                .buyNowPrice(buyNowPrice)
                .startTime(effectiveStartTime)
                .endTime(endTime)
                .build();

        auction = auctionRepository.save(auction);

        if (imageUrls != null && !imageUrls.isEmpty()) {
            Auction savedAuction = auction;
            List<AuctionImage> images = IntStream.range(0, imageUrls.size())
                    .mapToObj(i -> AuctionImage.builder()
                            .auction(savedAuction)
                            .imageUrl(imageUrls.get(i))
                            .sortOrder(i)
                            .build())
                    .toList();
            auction.updateImages(images);
        }

        auctionEventPublisher.publishAuctionCreated(auction);
        auctionLifecyclePort.scheduleActivation(auction.getId(), auction.getStartTime());
        log.info("경매 등록: auctionId={}, sellerId={}, startingPrice={}", auction.getId(), sellerId, startingPrice);
        return auction;
    }

    @Transactional
    public Auction update(Long auctionId, Long memberId, Long categoryId, String title, String description,
                          ItemCondition condition, Long startingPrice, Long buyNowPrice,
                          LocalDateTime endTime, List<String> imageUrls) {
        Auction auction = findAuctionById(auctionId);
        validateSeller(auction, memberId);
        validatePending(auction);

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(AuctionErrorCode.CATEGORY_NOT_FOUND));
        validateAuctionTime(auction.getStartTime(), endTime);
        validateAuctionDuration(auction.getStartTime(), endTime);

        auction.update(category, title, description, condition, startingPrice, buyNowPrice, endTime);

        if (imageUrls != null) {
            List<AuctionImage> images = IntStream.range(0, imageUrls.size())
                    .mapToObj(i -> AuctionImage.builder()
                            .auction(auction)
                            .imageUrl(imageUrls.get(i))
                            .sortOrder(i)
                            .build())
                    .toList();
            auction.updateImages(images);
        }

        auctionEventPublisher.publishAuctionUpdated(auction);
        return auction;
    }

    @Transactional
    public void cancel(Long auctionId, Long memberId) {
        Auction auction = findAuctionById(auctionId);
        validateSeller(auction, memberId);
        validatePending(auction);
        auction.cancel();
        auctionLifecyclePort.cancelSchedule(auction.getId());
        auctionEventPublisher.publishAuctionCancelled(auction);
        log.info("경매 취소: auctionId={}, sellerId={}", auctionId, memberId);
    }

    @Transactional
    public void forceCancel(Long auctionId) {
        Auction auction = findAuctionById(auctionId);
        if (auction.getStatus() == AuctionStatus.CANCELLED) {
            throw new BusinessException(AuctionErrorCode.AUCTION_ALREADY_CANCELLED);
        }
        auction.cancel();
        auctionLifecyclePort.cancelSchedule(auction.getId());
        auctionEventPublisher.publishAuctionCancelled(auction);
    }

    @Transactional
    public void activateAuction(Long auctionId) {
        Auction auction = findAuctionById(auctionId);
        if (!auction.isPending()) {
            return;
        }
        auction.activate();
        auctionRepository.save(auction);
        auctionLifecyclePort.scheduleEnd(auction.getId(), auction.getEndTime());
        auctionEventPublisher.publishAuctionActivated(auction);
        log.info("경매 활성화: auctionId={}, PENDING → ACTIVE", auctionId);
    }

    @Transactional
    public void endAuction(Long auctionId) {
        Auction auction = findAuctionById(auctionId);
        if (!auction.isActive()) {
            return;
        }
        auction.end();
        autoBidRepository.deactivateAllByAuctionId(auctionId);
        auctionRepository.save(auction);

        if (auction.getWinner() != null) {
            chatService.createRoom(auction);
        }

        auctionEventPublisher.publishAuctionEnded(auction);
        log.info("경매 종료: auctionId={}, ACTIVE → ENDED, winnerId={}, finalPrice={}",
                auctionId, auction.getWinner() != null ? auction.getWinner().getId() : "유찰",
                auction.getCurrentPrice());
    }

    @Transactional
    public void completeBuyNow(Auction auction, Member buyer) {
        auction.sell(buyer);
        autoBidRepository.deactivateAllByAuctionId(auction.getId());
        auctionLifecyclePort.cancelSchedule(auction.getId());
        chatService.createRoom(auction);
        auctionEventPublisher.publishAuctionSold(auction);
        log.info("즉시구매 완료: auctionId={}, buyerId={}, price={}",
                auction.getId(), buyer.getId(), auction.getBuyNowPrice());
    }

    public Auction findById(Long auctionId) {
        return auctionRepository.findByIdWithImages(auctionId)
                .orElseThrow(AuctionNotFoundException::new);
    }

    private Auction findAuctionById(Long auctionId) {
        return auctionRepository.findById(auctionId)
                .orElseThrow(AuctionNotFoundException::new);
    }

    private void validateSeller(Auction auction, Long memberId) {
        if (!auction.isSeller(memberId)) {
            throw new BusinessException(AuctionErrorCode.NOT_AUCTION_SELLER);
        }
    }

    private void validatePending(Auction auction) {
        if (!auction.isPending()) {
            throw new BusinessException(AuctionErrorCode.AUCTION_NOT_PENDING);
        }
    }

    private void validateAuctionTime(LocalDateTime startTime, LocalDateTime endTime) {
        if (endTime.isBefore(startTime) || endTime.isEqual(startTime)) {
            throw new BusinessException(AuctionErrorCode.INVALID_AUCTION_TIME);
        }
    }

    private void validateAuctionDuration(LocalDateTime startTime, LocalDateTime endTime) {
        Duration duration = Duration.between(startTime, endTime);
        if (duration.toHours() < 1 || duration.toDays() > 7) {
            throw new BusinessException(AuctionErrorCode.INVALID_AUCTION_DURATION);
        }
    }
}