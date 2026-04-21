package com.biddo.api.search.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KeywordAlertRequest {

    @NotBlank
    private String keyword;

    private Long categoryId;
    private Long maxPrice;
}