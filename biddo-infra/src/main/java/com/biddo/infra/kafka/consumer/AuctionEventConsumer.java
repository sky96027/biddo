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
    @KafkaListener(topics = KafkaConfig.AUCTION_EVENTS, groupId = "biddo-notification")
    public void handleAuctionEvent(AuctionEvent event) {
        log.info("Received {}: auctionId={}, sellerId={}, status={}, currentPrice={}",
                event.getEventType(), event.getAuctionId(), event.getSellerId(),
                event.getStatus(), event.getCurrentPrice());

        switch (event.getEventType()) {
            case AuctionEvent.AUCTION_SOLD -> handleAuctionSold(event);
            case AuctionEvent.AUCTION_ENDED -> handleAuctionEnded(event);
            case AuctionEvent.AUCTION_CANCELLED -> handleAuctionCancelled(event);
            default -> log.debug("Ignoring event type: {}", event.getEventType());
        }
    }

    private void handleAuctionSold(AuctionEvent event) {
        String formattedPrice = formatPrice(event.getCurrentPrice());
        Member seller = memberRepository.findById(event.getSellerId()).orElse(null);
        Member winner = event.getWinnerId() != null
                ? memberRepository.findById(event.getWinnerId()).orElse(null)
                : null;

        // 낙찰자에게 WON 알림
        if (winner != null) {
            notificationService.create(winner, event.getAuctionId(), NotificationType.WON,
                    String.format("즉시 구매가 완료되었습니다! (구매가: %s원)", formattedPrice));
        }

        // 판매자에게 AUCTION_END 알림
        if (seller != null) {
            notificationService.create(seller, event.getAuctionId(), NotificationType.AUCTION_END,
                    String.format("상품이 즉시 구매되었습니다. (판매가: %s원)", formattedPrice));
        }

        // 다른 입찰자들에게 AUCTION_END 알림
        notifyOtherBidders(event, "경매가 즉시 구매로 종료되었습니다.");
    }

    private void handleAuctionEnded(AuctionEvent event) {
        Member seller = memberRepository.findById(event.getSellerId()).orElse(null);

        if (event.getWinnerId() != null) {
            // 낙찰 성공
            String formattedPrice = formatPrice(event.getCurrentPrice());
            Member winner = memberRepository.findById(event.getWinnerId()).orElse(null);

            if (winner != null) {
                notificationService.create(winner, event.getAuctionId(), NotificationType.WON,
                        String.format("경매에서 낙찰되었습니다! (낙찰가: %s원)", formattedPrice));
            }
            if (seller != null) {
                notificationService.create(seller, event.getAuctionId(), NotificationType.AUCTION_END,
                        String.format("경매가 종료되었습니다. 낙찰자가 결정되었습니다. (낙찰가: %s원)", formattedPrice));
            }

            notifyOtherBidders(event, "경매가 종료되었습니다.");
        } else {
            // 유찰
            if (seller != null) {
                notificationService.create(seller, event.getAuctionId(), NotificationType.AUCTION_END,
                        "경매가 종료되었습니다. 입찰자가 없어 유찰되었습니다.");
            }
        }
    }

    private void handleAuctionCancelled(AuctionEvent event) {
        // 입찰자들에게 경매 취소 알림
        List<Long> bidderIds = bidRepository.findDistinctBidderIdsByAuctionId(event.getAuctionId());
        if (!bidderIds.isEmpty()) {
            List<Member> bidders = memberRepository.findAllById(bidderIds);
            for (Member bidder : bidders) {
                notificationService.create(bidder, event.getAuctionId(), NotificationType.AUCTION_END,
                        "참여 중인 경매가 취소되었습니다.");
            }
        }
    }

    private void notifyOtherBidders(AuctionEvent event, String message) {
        List<Long> bidderIds = bidRepository.findDistinctBidderIdsByAuctionId(event.getAuctionId());
        bidderIds.remove(event.getWinnerId());
        bidderIds.remove(event.getSellerId());

        if (!bidderIds.isEmpty()) {
            List<Member> otherBidders = memberRepository.findAllById(bidderIds);
            for (Member bidder : otherBidders) {
                notificationService.create(bidder, event.getAuctionId(), NotificationType.AUCTION_END, message);
            }
        }
    }

    private String formatPrice(Long price) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(price);
    }
}