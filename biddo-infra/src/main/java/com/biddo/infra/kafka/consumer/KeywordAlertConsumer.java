package com.biddo.infra.kafka.consumer;

import com.biddo.domain.notification.entity.NotificationType;
import com.biddo.domain.notification.service.NotificationService;
import com.biddo.domain.search.entity.KeywordAlert;
import com.biddo.domain.search.service.KeywordAlertService;
import com.biddo.infra.auction.AuctionJpaRepository;
import com.biddo.infra.kafka.KafkaConfig;
import com.biddo.infra.kafka.event.AuctionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeywordAlertConsumer {

    private final KeywordAlertService keywordAlertService;
    private final NotificationService notificationService;
    private final AuctionJpaRepository auctionJpaRepository;

    @Transactional
    @KafkaListener(topics = KafkaConfig.AUCTION_EVENTS, groupId = "keyword-alert")
    public void handleAuctionCreated(AuctionEvent event) {
        if (!AuctionEvent.AUCTION_CREATED.equals(event.getEventType())
                && !AuctionEvent.AUCTION_ACTIVATED.equals(event.getEventType())) {
            return;
        }

        log.info("Keyword alert check for auctionId={}", event.getAuctionId());

        auctionJpaRepository.findByIdWithSeller(event.getAuctionId()).ifPresent(auction -> {
            List<KeywordAlert> activeAlerts = keywordAlertService.findAllActive();

            for (KeywordAlert alert : activeAlerts) {
                // 자기 경매에는 알림 안 보냄
                if (alert.getMember().getId().equals(auction.getSeller().getId())) {
                    continue;
                }

                if (alert.matches(auction.getTitle(), auction.getCategory().getId(), auction.getCurrentPrice())) {
                    String message = String.format("키워드 \"%s\"에 매칭되는 새 경매가 등록되었습니다: %s",
                            alert.getKeyword(), auction.getTitle());

                    notificationService.create(
                            alert.getMember(),
                            auction.getId(),
                            NotificationType.KEYWORD_MATCH,
                            message
                    );

                    log.info("Keyword alert sent: alertId={}, memberId={}, auctionId={}",
                            alert.getId(), alert.getMember().getId(), auction.getId());
                }
            }
        });
    }
}