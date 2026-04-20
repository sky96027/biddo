package com.biddo.infra.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String BID_EVENTS = "bid-events";
    public static final String AUCTION_EVENTS = "auction-events";
    public static final String NOTIFICATION_EVENTS = "notification-events";
    public static final String CHAT_EVENTS = "chat-events";

    @Bean
    public NewTopic bidEventsTopic() {
        return TopicBuilder.name(BID_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic auctionEventsTopic() {
        return TopicBuilder.name(AUCTION_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic notificationEventsTopic() {
        return TopicBuilder.name(NOTIFICATION_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic chatEventsTopic() {
        return TopicBuilder.name(CHAT_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
