package com.biddo.api.notification.dto.response;

import com.biddo.domain.notification.entity.PriceAlert;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PriceAlertResponse {

    private Long id;
    private Long auctionId;
    private int thresholdPercent;
    private Long basePrice;
    private boolean isActive;

    public static PriceAlertResponse from(PriceAlert alert) {
        return PriceAlertResponse.builder()
                .id(alert.getId())
                .auctionId(alert.getAuctionId())
                .thresholdPercent(alert.getThresholdPercent())
                .basePrice(alert.getBasePrice())
                .isActive(alert.isActive())
                .build();
    }
}