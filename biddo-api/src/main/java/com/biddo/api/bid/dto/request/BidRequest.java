package com.biddo.api.bid.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BidRequest {

    @NotNull(message = "입찰 금액은 필수입니다.")
    private Long bidAmount;
}