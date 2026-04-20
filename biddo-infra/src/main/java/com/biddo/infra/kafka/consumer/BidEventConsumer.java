package com.biddo.infra.kafka.consumer;

import com.biddo.domain.auction.model.Auction;
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
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class BidEventConsumer {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    @Transactional
    @KafkaListener(topics = KafkaConfig.BID_EVENTS, groupId = "biddo-notification")
    public void handleBidEvent(BidEvent event) {
        log.info("Received {}: auctionId={}, bidId={}, bidderId={}, amount={}, type={}",
                event.getEventType(), event.getAuctionId(), event.getBidId(),
                event.getBidderId(), event.getBidAmount(), event.getBidType());

        if (!BidEvent.BID_PLACED.equals(event.getEventType())) {
            return;
        }

        Auction auction = auctionRepository.findById(event.getAuctionId()).orElse(null);
        if (auction == null) {
            log.warn("Auction not found: auctionId={}", event.getAuctionId());
            return;
        }

        String formattedAmount = formatPrice(event.getBidAmount());
        String auctionTitle = auction.getTitle();

        // 판매자에게 BID 알림
        Member seller = auction.getSeller();
        if (!seller.getId().equals(event.getBidderId())) {
            notificationService.create(seller, event.getAuctionId(), NotificationType.BID,
                    String.format("[%s] 새로운 입찰이 등록되었습니다. (입찰가: %s원)", auctionTitle, formattedAmount));
        }

        // 다른 입찰자들에게 OUTBID 알림
        List<Long> bidderIds = bidRepository.findDistinctBidderIdsByAuctionId(event.getAuctionId());
        bidderIds.remove(event.getBidderId()); // 현재 입찰자 제외
        bidderIds.remove(seller.getId());      // 판매자 제외 (이미 BID 알림 전송)

        if (!bidderIds.isEmpty()) {
            List<Member> outbidMembers = memberRepository.findAllById(bidderIds);
            for (Member member : outbidMembers) {
                notificationService.create(member, event.getAuctionId(), NotificationType.OUTBID,
                        String.format("[%s] 입찰이 추월되었습니다. (현재 최고가: %s원)", auctionTitle, formattedAmount));
            }
        }
    }

    private String formatPrice(Long price) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(price);
    }
}