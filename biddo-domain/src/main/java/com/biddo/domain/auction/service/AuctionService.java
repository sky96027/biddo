package com.biddo.domain.auction.service;

import com.biddo.domain.auction.exception.AuctionErrorCode;
import com.biddo.domain.auction.exception.AuctionNotFoundException;
import com.biddo.domain.auction.model.Auction;
import com.biddo.domain.auction.model.AuctionImage;
import com.biddo.domain.auction.model.AuctionStatus;
import com.biddo.domain.auction.model.ItemCondition;
import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.category.entity.Category;
import com.biddo.domain.category.repository.CategoryRepository;
import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.exception.MemberErrorCode;
import com.biddo.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final MemberRepository memberRepository;
    private final CategoryRepository categoryRepository;

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

        return auction;
    }

    @Transactional
    public void cancel(Long auctionId, Long memberId) {
        Auction auction = findAuctionById(auctionId);
        validateSeller(auction, memberId);
        validatePending(auction);
        auction.cancel();
    }

    @Transactional
    public void forceCancel(Long auctionId) {
        Auction auction = findAuctionById(auctionId);
        if (auction.getStatus() == AuctionStatus.CANCELLED) {
            throw new BusinessException(AuctionErrorCode.AUCTION_ALREADY_CANCELLED);
        }
        auction.cancel();
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
