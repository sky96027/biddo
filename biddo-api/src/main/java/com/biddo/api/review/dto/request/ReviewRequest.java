package com.biddo.api.review.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReviewRequest {

    @NotNull(message = "별점은 필수입니다.")
    private Integer rating;

    private String content;
}