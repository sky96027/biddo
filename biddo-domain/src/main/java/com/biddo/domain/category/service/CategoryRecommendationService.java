package com.biddo.domain.category.service;

import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.bid.port.out.BidRepository;
import com.biddo.domain.category.entity.Category;
import com.biddo.domain.category.repository.CategoryRepository;
import com.biddo.domain.notification.port.out.PriceAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryRecommendationService {

    private static final int MAX_RECOMMENDATIONS = 5;
    private static final int BID_CATEGORY_LIMIT = 10;

    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;
    private final PriceAlertRepository priceAlertRepository;
    private final CategoryRepository categoryRepository;

    public List<Category> recommend(Long memberId) {
        LinkedHashSet<Long> categoryIds = new LinkedHashSet<>();

        addBidBasedCategories(memberId, categoryIds);
        addPriceAlertBasedCategories(memberId, categoryIds);

        if (categoryIds.isEmpty()) {
            return List.of();
        }

        List<Long> topIds = categoryIds.stream()
                .limit(MAX_RECOMMENDATIONS)
                .toList();

        return categoryRepository.findAllById(topIds);
    }

    private void addBidBasedCategories(Long memberId, LinkedHashSet<Long> categoryIds) {
        List<Long> bidCategoryIds = bidRepository.findTopCategoryIdsByBidderId(memberId, BID_CATEGORY_LIMIT);
        categoryIds.addAll(bidCategoryIds);
    }

    private void addPriceAlertBasedCategories(Long memberId, LinkedHashSet<Long> categoryIds) {
        List<Long> auctionIds = priceAlertRepository.findActiveAuctionIdsByMemberId(memberId);
        if (auctionIds.isEmpty()) return;

        for (Long auctionId : auctionIds) {
            auctionRepository.findById(auctionId)
                    .ifPresent(auction -> categoryIds.add(auction.getCategory().getId()));
        }
    }
}