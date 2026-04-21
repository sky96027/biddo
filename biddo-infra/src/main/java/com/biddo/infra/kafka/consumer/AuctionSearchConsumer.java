package com.biddo.infra.kafka.consumer;

import com.biddo.domain.auction.model.Auction;
import com.biddo.infra.auction.AuctionJpaRepository;
import com.biddo.infra.elasticsearch.document.AuctionDocument;
import com.biddo.infra.elasticsearch.repository.AuctionDocumentRepository;
import com.biddo.infra.kafka.KafkaConfig;
import com.biddo.infra.kafka.event.AuctionEvent;
import com.biddo.infra.kafka.event.BidEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionSearchConsumer {

    private final AuctionDocumentRepository auctionDocumentRepository;
    private final AuctionJpaRepository auctionJpaRepository;

    @KafkaListener(topics = KafkaConfig.AUCTION_EVENTS, groupId = "search-sync")
    public void handleAuctionEvent(AuctionEvent event) {
        log.info("Search sync - auction event: {} for auctionId={}", event.getEventType(), event.getAuctionId());

        switch (event.getEventType()) {
            case AuctionEvent.AUCTION_CREATED, AuctionEvent.AUCTION_UPDATED, AuctionEvent.AUCTION_ACTIVATED -> indexAuction(event.getAuctionId());
            case AuctionEvent.AUCTION_CANCELLED -> deleteAuction(event.getAuctionId());
            case AuctionEvent.AUCTION_ENDED, AuctionEvent.AUCTION_SOLD -> updateAuctionStatus(event.getAuctionId(), event.getStatus());
        }
    }

    @KafkaListener(topics = KafkaConfig.BID_EVENTS, groupId = "search-sync")
    public void handleBidEvent(BidEvent event) {
        if (BidEvent.BID_PLACED.equals(event.getEventType())) {
            log.info("Search sync - bid event for auctionId={}", event.getAuctionId());
            auctionJpaRepository.findById(event.getAuctionId()).ifPresent(auction ->
                    auctionDocumentRepository.findById(event.getAuctionId()).ifPresent(doc -> {
                        doc.updatePrice(auction.getCurrentPrice(), auction.getBidCount());
                        auctionDocumentRepository.save(doc);
                    })
            );
        }
    }

    private void indexAuction(Long auctionId) {
        auctionJpaRepository.findByIdWithImages(auctionId).ifPresent(auction -> {
            AuctionDocument document = toDocument(auction);
            auctionDocumentRepository.save(document);
            log.info("Indexed auction: {}", auctionId);
        });
    }

    private void deleteAuction(Long auctionId) {
        auctionDocumentRepository.deleteById(auctionId);
        log.info("Deleted auction from index: {}", auctionId);
    }

    private void updateAuctionStatus(Long auctionId, String status) {
        auctionDocumentRepository.findById(auctionId).ifPresent(doc -> {
            doc.updateStatus(status);
            auctionDocumentRepository.save(doc);
        });
    }

    private AuctionDocument toDocument(Auction auction) {
        String thumbnail = auction.getImages().isEmpty()
                ? null
                : auction.getImages().get(0).getImageUrl();

        return AuctionDocument.builder()
                .id(auction.getId())
                .title(auction.getTitle())
                .description(auction.getDescription())
                .status(auction.getStatus().name())
                .currentPrice(auction.getCurrentPrice())
                .buyNowPrice(auction.getBuyNowPrice())
                .bidCount(auction.getBidCount())
                .thumbnailUrl(thumbnail)
                .endTime(auction.getEndTime())
                .categoryId(auction.getCategory().getId())
                .categoryName(auction.getCategory().getName())
                .sellerId(auction.getSeller().getId())
                .sellerNickname(auction.getSeller().getNickname())
                .build();
    }
}