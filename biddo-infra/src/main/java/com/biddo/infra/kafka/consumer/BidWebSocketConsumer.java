package com.biddo.infra.kafka.consumer;

import com.biddo.domain.auction.entity.Auction;
import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.repository.MemberRepository;
import com.biddo.infra.kafka.KafkaConfig;
import com.biddo.infra.kafka.event.BidEvent;
import com.biddo.infra.sse.AuctionSseService;
import com.biddo.infra.websocket.dto.AuctionWebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class BidWebSocketConsumer {

    private final AuctionRepository auctionRepository;
    private final MemberRepository memberRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AuctionSseService auctionSseService;

    @Transactional(readOnly = true)
    @KafkaListener(topics = KafkaConfig.BID_EVENTS,
            groupId = "#{T(java.util.UUID).randomUUID().toString()}")
    public void handleBidEvent(BidEvent event) {
        if (!BidEvent.BID_PLACED.equals(event.getEventType())) {
            return;
        }

        Auction auction = auctionRepository.findById(event.getAuctionId()).orElse(null);
        if (auction == null) {
            log.warn("Auction not found for WebSocket broadcast: auctionId={}", event.getAuctionId());
            return;
        }

        String bidderNickname = memberRepository.findById(event.getBidderId())
                .map(Member::getNickname)
                .orElse("알 수 없음");

        AuctionWebSocketMessage message = AuctionWebSocketMessage.builder()
                .type(AuctionWebSocketMessage.NEW_BID)
                .auctionId(event.getAuctionId())
                .bidId(event.getBidId())
                .bidderId(event.getBidderId())
                .bidderNickname(bidderNickname)
                .bidAmount(event.getBidAmount())
                .currentPrice(auction.getCurrentPrice())
                .bidCount(auction.getBidCount())
                .bidType(event.getBidType())
                .endTime(auction.getEndTime())
                .occurredAt(event.getOccurredAt())
                .build();

        String destination = "/topic/auction/" + event.getAuctionId();
        messagingTemplate.convertAndSend(destination, message);

        // SSE 카운트다운 동기화 (endTime이 변경된 경우 = 스나이핑 방지 연장)
        auctionSseService.broadcastCountdownExtended(event.getAuctionId(), auction.getEndTime());

        log.debug("WebSocket broadcast to {}: type={}, bidAmount={}", destination, message.getType(), event.getBidAmount());
    }
}