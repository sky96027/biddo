package com.biddo.api.bid.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BidRequest {

    @NotNull(message = "입찰 금액은 필수입니다.")
    @Positive(message = "입찰 금액은 양수여야 합니다.")
    private Long bidAmount;
}