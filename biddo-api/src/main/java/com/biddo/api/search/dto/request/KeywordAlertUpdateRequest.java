package com.biddo.api.search.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KeywordAlertUpdateRequest {

    private Long categoryId;
    private Long maxPrice;
}