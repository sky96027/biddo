package com.biddo.domain.search.entity;

import com.biddo.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "keyword_alert", uniqueConstraints = {
        @UniqueConstraint(name = "uk_keyword_alert_member_keyword", columnNames = {"member_id", "keyword"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KeywordAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "keyword_alert_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 100)
    private String keyword;

    @Column(name = "category_id")
    private Long categoryId;

    private Long maxPrice;

    @Column(nullable = false)
    private boolean isActive;

    @Builder
    public KeywordAlert(Member member, String keyword, Long categoryId, Long maxPrice) {
        this.member = member;
        this.keyword = keyword;
        this.categoryId = categoryId;
        this.maxPrice = maxPrice;
        this.isActive = true;
    }

    public void update(Long categoryId, Long maxPrice) {
        this.categoryId = categoryId;
        this.maxPrice = maxPrice;
    }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public boolean isOwner(Long memberId) {
        return this.member.getId().equals(memberId);
    }

    public boolean matches(String title, Long auctionCategoryId, Long auctionPrice) {
        if (!this.isActive) return false;
        if (!title.toLowerCase().contains(this.keyword.toLowerCase())) return false;
        if (this.categoryId != null && !this.categoryId.equals(auctionCategoryId)) return false;
        if (this.maxPrice != null && auctionPrice > this.maxPrice) return false;
        return true;
    }
}