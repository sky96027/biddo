package com.biddo.api.auction.dto.request;

import com.biddo.domain.auction.model.ItemCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class AuctionCreateRequest {

    @NotBlank(message = "상품명은 필수입니다.")
    private String title;

    @NotBlank(message = "상품 설명은 필수입니다.")
    private String description;

    @NotNull(message = "카테고리는 필수입니다.")
    private Long categoryId;

    @NotNull(message = "상품 상태는 필수입니다.")
    private ItemCondition condition;

    @NotNull(message = "시작가는 필수입니다.")
    private Long startingPrice;

    private Long buyNowPrice;

    private LocalDateTime startTime;

    @NotNull(message = "종료 시간은 필수입니다.")
    private LocalDateTime endTime;

    private List<String> imageUrls;
}