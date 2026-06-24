package com.biddo.infra.kafka.consumer;

import com.biddo.domain.auction.entity.Auction;
import com.biddo.domain.auction.port.out.AuctionRepository;
import com.biddo.domain.notification.entity.NotificationType;
import com.biddo.domain.notification.entity.PriceAlert;
import com.biddo.domain.notification.service.NotificationService;
import com.biddo.domain.notification.service.PriceAlertService;
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
public class PriceAlertConsumer {

    private final PriceAlertService priceAlertService;
    private final NotificationService notificationService;
    private final AuctionRepository auctionRepository;

    @Transactional
    @KafkaListener(topics = KafkaConfig.BID_EVENTS, groupId = "biddo-price-alert")
    public void handleBidEvent(BidEvent event) {
        if (!BidEvent.BID_PLACED.equals(event.getEventType())) {
            return;
        }

        List<PriceAlert> alerts = priceAlertService.findActiveByAuctionId(event.getAuctionId());
        if (alerts.isEmpty()) {
            return;
        }

        Auction auction = auctionRepository.findById(event.getAuctionId()).orElse(null);
        if (auction == null) {
            return;
        }

        String formattedAmount = formatPrice(event.getBidAmount());
        String auctionTitle = auction.getTitle();

        for (PriceAlert alert : alerts) {
            if (alert.getMember().getId().equals(event.getBidderId())) {
                continue;
            }

            if (alert.isTriggered(event.getBidAmount())) {
                long increasePercent = Math.round(
                        ((double) (event.getBidAmount() - alert.getBasePrice()) / alert.getBasePrice()) * 100
                );

                notificationService.create(
                        alert.getMember(),
                        event.getAuctionId(),
                        NotificationType.PRICE_ALERT,
                        String.format("[%s] 가격이 %d%% 상승했습니다. (현재가: %s원)", auctionTitle, increasePercent, formattedAmount)
                );

                alert.updateBasePrice(event.getBidAmount());
                log.info("Price alert triggered: alertId={}, auctionId={}, increase={}%",
                        alert.getId(), event.getAuctionId(), increasePercent);
            }
        }
    }

    private String formatPrice(Long price) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(price);
    }
}