package com.biddo.api.notification.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PriceAlertRequest {

    @NotNull
    private Long auctionId;

    @NotNull
    private Integer thresholdPercent;
}