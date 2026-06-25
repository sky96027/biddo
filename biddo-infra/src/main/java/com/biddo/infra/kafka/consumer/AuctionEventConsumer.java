package com.biddo.infra.kafka.consumer;

import com.biddo.domain.bid.port.out.BidRepository;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.repository.MemberRepository;
import com.biddo.domain.notification.entity.NotificationType;
import com.biddo.domain.notification.service.NotificationService;
import com.biddo.infra.kafka.KafkaConfig;
import com.biddo.infra.kafka.event.AuctionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionEventConsumer {

    private final BidRepository bidRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    @Transactional
    @KafkaListener(
            topics = KafkaConfig.AUCTION_EVENTS,
            groupId = "biddo-notification",
            containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void handleAuctionEvents(List<AuctionEvent> events) {
        log.debug("Processing auction event batch: size={}", events.size());

        List<NotificationService.NotificationSpec> specs = new ArrayList<>();

        for (AuctionEvent event : events) {
            try {
                collectSpecs(event, specs);
            } catch (Exception e) {
                log.error("Failed to process auction event: auctionId={}, type={}",
                        event.getAuctionId(), event.getEventType(), e);
            }
        }

        notificationService.createAll(specs);
    }

    private void collectSpecs(AuctionEvent event, List<NotificationService.NotificationSpec> specs) {
        switch (event.getEventType()) {
            case AuctionEvent.AUCTION_SOLD -> collectSoldSpecs(event, specs);
            case AuctionEvent.AUCTION_ENDED -> collectEndedSpecs(event, specs);
            case AuctionEvent.AUCTION_CANCELLED -> collectCancelledSpecs(event, specs);
            default -> log.debug("Ignoring event type: {}", event.getEventType());
        }
    }

    private void collectSoldSpecs(AuctionEvent event, List<NotificationService.NotificationSpec> specs) {
        String formattedPrice = formatPrice(event.getCurrentPrice());
        Member seller = memberRepository.findById(event.getSellerId()).orElse(null);
        Member winner = event.getWinnerId() != null
                ? memberRepository.findById(event.getWinnerId()).orElse(null)
                : null;

        if (winner != null) {
            specs.add(new NotificationService.NotificationSpec(
                    winner, event.getAuctionId(), NotificationType.WON,
                    String.format("즉시 구매가 완료되었습니다! (구매가: %s원)", formattedPrice)
            ));
        }
        if (seller != null) {
            specs.add(new NotificationService.NotificationSpec(
                    seller, event.getAuctionId(), NotificationType.AUCTION_END,
                    String.format("상품이 즉시 구매되었습니다. (판매가: %s원)", formattedPrice)
            ));
        }

        collectOtherBidderSpecs(event, "경매가 즉시 구매로 종료되었습니다.", specs);
    }

    private void collectEndedSpecs(AuctionEvent event, List<NotificationService.NotificationSpec> specs) {
        Member seller = memberRepository.findById(event.getSellerId()).orElse(null);

        if (event.getWinnerId() != null) {
            String formattedPrice = formatPrice(event.getCurrentPrice());
            Member winner = memberRepository.findById(event.getWinnerId()).orElse(null);

            if (winner != null) {
                specs.add(new NotificationService.NotificationSpec(
                        winner, event.getAuctionId(), NotificationType.WON,
                        String.format("경매에서 낙찰되었습니다! (낙찰가: %s원)", formattedPrice)
                ));
            }
            if (seller != null) {
                specs.add(new NotificationService.NotificationSpec(
                        seller, event.getAuctionId(), NotificationType.AUCTION_END,
                        String.format("경매가 종료되었습니다. 낙찰자가 결정되었습니다. (낙찰가: %s원)", formattedPrice)
                ));
            }
            collectOtherBidderSpecs(event, "경매가 종료되었습니다.", specs);
        } else {
            if (seller != null) {
                specs.add(new NotificationService.NotificationSpec(
                        seller, event.getAuctionId(), NotificationType.AUCTION_END,
                        "경매가 종료되었습니다. 입찰자가 없어 유찰되었습니다."
                ));
            }
        }
    }

    private void collectCancelledSpecs(AuctionEvent event, List<NotificationService.NotificationSpec> specs) {
        List<Long> bidderIds = bidRepository.findDistinctBidderIdsByAuctionId(event.getAuctionId());
        if (!bidderIds.isEmpty()) {
            memberRepository.findAllById(bidderIds).forEach(bidder ->
                    specs.add(new NotificationService.NotificationSpec(
                            bidder, event.getAuctionId(), NotificationType.AUCTION_END,
                            "참여 중인 경매가 취소되었습니다."
                    ))
            );
        }
    }

    private void collectOtherBidderSpecs(AuctionEvent event, String message, List<NotificationService.NotificationSpec> specs) {
        List<Long> bidderIds = bidRepository.findDistinctBidderIdsByAuctionId(event.getAuctionId());
        bidderIds.remove(event.getWinnerId());
        bidderIds.remove(event.getSellerId());

        if (!bidderIds.isEmpty()) {
            memberRepository.findAllById(bidderIds).forEach(bidder ->
                    specs.add(new NotificationService.NotificationSpec(
                            bidder, event.getAuctionId(), NotificationType.AUCTION_END, message
                    ))
            );
        }
    }

    private String formatPrice(Long price) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(price);
    }
}