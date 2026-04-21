package com.biddo.api.search.dto.response;

import com.biddo.domain.search.entity.KeywordAlert;
import lombok.Getter;

@Getter
public class KeywordAlertResponse {

    private final Long alertId;
    private final String keyword;
    private final Long categoryId;
    private final Long maxPrice;
    private final boolean isActive;

    private KeywordAlertResponse(Long alertId, String keyword, Long categoryId,
                                  Long maxPrice, boolean isActive) {
        this.alertId = alertId;
        this.keyword = keyword;
        this.categoryId = categoryId;
        this.maxPrice = maxPrice;
        this.isActive = isActive;
    }

    public static KeywordAlertResponse from(KeywordAlert alert) {
        return new KeywordAlertResponse(
                alert.getId(),
                alert.getKeyword(),
                alert.getCategoryId(),
                alert.getMaxPrice(),
                alert.isActive()
        );
    }
}