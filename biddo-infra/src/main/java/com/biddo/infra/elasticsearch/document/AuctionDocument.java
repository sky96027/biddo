package com.biddo.infra.elasticsearch.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Document(indexName = "auctions")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Long)
    private Long currentPrice;

    @Field(type = FieldType.Long)
    private Long buyNowPrice;

    @Field(type = FieldType.Integer)
    private int bidCount;

    @Field(type = FieldType.Keyword)
    private String thumbnailUrl;

    @Field(type = FieldType.Date)
    private LocalDateTime endTime;

    @Field(type = FieldType.Long)
    private Long categoryId;

    @Field(type = FieldType.Keyword)
    private String categoryName;

    @Field(type = FieldType.Long)
    private Long sellerId;

    @Field(type = FieldType.Keyword)
    private String sellerNickname;

    public void updatePrice(Long currentPrice, int bidCount) {
        this.currentPrice = currentPrice;
        this.bidCount = bidCount;
    }

    public void updateStatus(String status) {
        this.status = status;
    }
}