package com.biddo.infra.kafka.consumer;

import com.biddo.domain.auction.entity.Auction;
import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.bid.port.out.BidRepository;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.repository.MemberRepository;
import com.biddo.domain.notification.entity.NotificationType;
import com.biddo.domain.notification.service.NotificationService;
import com.biddo.infra.kafka.KafkaConfig;
import com.biddo.infra.kafka.event.BidEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class BidEventConsumer {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    @Transactional
    @KafkaListener(
            topics = KafkaConfig.BID_EVENTS,
            groupId = "biddo-notification",
            containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void handleBidEvents(List<BidEvent> events) {
        log.debug("Processing bid event batch: size={}", events.size());

        List<Long> auctionIds = events.stream()
                .map(BidEvent::getAuctionId)
                .distinct()
                .toList();

        Map<Long, Auction> auctionMap = auctionRepository.findByIdIn(auctionIds).stream()
                .collect(Collectors.toMap(Auction::getId, Function.identity()));

        List<NotificationService.NotificationSpec> specs = new ArrayList<>();

        for (BidEvent event : events) {
            try {
                if (!BidEvent.BID_PLACED.equals(event.getEventType())) continue;

                Auction auction = auctionMap.get(event.getAuctionId());
                if (auction == null) {
                    log.warn("Auction not found: auctionId={}", event.getAuctionId());
                    continue;
                }

                collectSpecs(event, auction, specs);
            } catch (Exception e) {
                log.error("Failed to process bid event: auctionId={}, bidderId={}",
                        event.getAuctionId(), event.getBidderId(), e);
            }
        }

        notificationService.createAll(specs);
    }

    private void collectSpecs(BidEvent event, Auction auction, List<NotificationService.NotificationSpec> specs) {
        String formattedAmount = formatPrice(event.getBidAmount());
        String auctionTitle = auction.getTitle();
        Member seller = auction.getSeller();

        if (!seller.getId().equals(event.getBidderId())) {
            specs.add(new NotificationService.NotificationSpec(
                    seller, event.getAuctionId(), NotificationType.BID,
                    String.format("[%s] 새로운 입찰이 등록되었습니다. (입찰가: %s원)", auctionTitle, formattedAmount)
            ));
        }

        List<Long> bidderIds = bidRepository.findDistinctBidderIdsByAuctionId(event.getAuctionId());
        bidderIds.remove(event.getBidderId());
        bidderIds.remove(seller.getId());

        if (!bidderIds.isEmpty()) {
            List<Member> outbidMembers = memberRepository.findAllById(bidderIds);
            outbidMembers.forEach(member ->
                    specs.add(new NotificationService.NotificationSpec(
                            member, event.getAuctionId(), NotificationType.OUTBID,
                            String.format("[%s] 입찰이 추월되었습니다. (현재 최고가: %s원)", auctionTitle, formattedAmount)
                    ))
            );
        }
    }

    private String formatPrice(Long price) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(price);
    }
}