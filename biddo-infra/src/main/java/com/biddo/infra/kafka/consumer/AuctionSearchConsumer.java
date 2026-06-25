package com.biddo.infra.kafka.consumer;

import com.biddo.domain.auction.entity.Auction;
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

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionSearchConsumer {

    private final AuctionDocumentRepository auctionDocumentRepository;
    private final AuctionJpaRepository auctionJpaRepository;

    @KafkaListener(
            topics = KafkaConfig.BID_EVENTS,
            groupId = "search-sync",
            containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void handleBidEvent(List<BidEvent> events) {
        List<Long> auctionIds = events.stream()
                .filter(e -> BidEvent.BID_PLACED.equals(e.getEventType()))
                .map(BidEvent::getAuctionId)
                .distinct()
                .collect(Collectors.toList());

        if (auctionIds.isEmpty()) {
            return;
        }

        Map<Long, Auction> auctionMap = auctionJpaRepository.findAllById(auctionIds)
                .stream().collect(Collectors.toMap(Auction::getId, a -> a));

        Map<Long, AuctionDocument> docMap = new HashMap<>();
        auctionDocumentRepository.findAllById(auctionIds)
                .forEach(doc -> docMap.put(doc.getId(), doc));

        List<AuctionDocument> toSave = auctionIds.stream()
                .filter(id -> auctionMap.containsKey(id) && docMap.containsKey(id))
                .map(id -> {
                    AuctionDocument doc = docMap.get(id);
                    Auction auction = auctionMap.get(id);
                    doc.updatePrice(auction.getCurrentPrice(), auction.getBidCount());
                    return doc;
                })
                .collect(Collectors.toList());

        if (!toSave.isEmpty()) {
            auctionDocumentRepository.saveAll(toSave);
            log.info("Search sync - bid batch: updated {} auctions", toSave.size());
        }
    }

    @KafkaListener(
            topics = KafkaConfig.AUCTION_EVENTS,
            groupId = "search-sync",
            containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void handleAuctionEvent(List<AuctionEvent> events) {
        List<Long> toIndex = new ArrayList<>();
        List<Long> toDelete = new ArrayList<>();
        Map<Long, String> toUpdateStatus = new LinkedHashMap<>();

        for (AuctionEvent event : events) {
            switch (event.getEventType()) {
                case AuctionEvent.AUCTION_CREATED, AuctionEvent.AUCTION_UPDATED, AuctionEvent.AUCTION_ACTIVATED ->
                        toIndex.add(event.getAuctionId());
                case AuctionEvent.AUCTION_CANCELLED ->
                        toDelete.add(event.getAuctionId());
                case AuctionEvent.AUCTION_ENDED, AuctionEvent.AUCTION_SOLD ->
                        toUpdateStatus.put(event.getAuctionId(), event.getStatus());
            }
        }

        if (!toIndex.isEmpty()) {
            List<Long> distinctIds = toIndex.stream().distinct().collect(Collectors.toList());
            List<AuctionDocument> docs = auctionJpaRepository.findAllByIdWithImages(distinctIds)
                    .stream().map(this::toDocument).collect(Collectors.toList());
            auctionDocumentRepository.saveAll(docs);
            log.info("Search sync - auction batch: indexed {} auctions", docs.size());
        }

        if (!toDelete.isEmpty()) {
            List<Long> distinctIds = toDelete.stream().distinct().collect(Collectors.toList());
            auctionDocumentRepository.deleteAllById(distinctIds);
            log.info("Search sync - auction batch: deleted {} auctions", distinctIds.size());
        }

        if (!toUpdateStatus.isEmpty()) {
            List<AuctionDocument> docs = new ArrayList<>();
            auctionDocumentRepository.findAllById(toUpdateStatus.keySet()).forEach(doc -> {
                doc.updateStatus(toUpdateStatus.get(doc.getId()));
                docs.add(doc);
            });
            if (!docs.isEmpty()) {
                auctionDocumentRepository.saveAll(docs);
                log.info("Search sync - auction batch: status updated {} auctions", docs.size());
            }
        }
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