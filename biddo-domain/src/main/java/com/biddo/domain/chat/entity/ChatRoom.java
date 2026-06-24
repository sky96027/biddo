package com.biddo.domain.chat.entity;

import com.biddo.domain.auction.entity.Auction;
import com.biddo.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_room")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_room_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private Member seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private Member buyer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRoomStatus status;

    @Column(name = "seller_last_read_message_id")
    private Long sellerLastReadMessageId;

    @Column(name = "buyer_last_read_message_id")
    private Long buyerLastReadMessageId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ChatRoom(Auction auction, Member seller, Member buyer) {
        this.auction = auction;
        this.seller = seller;
        this.buyer = buyer;
        this.status = ChatRoomStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
    }

    public void close() {
        this.status = ChatRoomStatus.CLOSED;
    }

    public boolean isParticipant(Long memberId) {
        return this.seller.getId().equals(memberId) || this.buyer.getId().equals(memberId);
    }

    public boolean isSeller(Long memberId) {
        return this.seller.getId().equals(memberId);
    }

    public void updateLastReadMessageId(Long memberId, Long messageId) {
        if (isSeller(memberId)) {
            this.sellerLastReadMessageId = messageId;
        } else {
            this.buyerLastReadMessageId = messageId;
        }
    }

    public Long getLastReadMessageId(Long memberId) {
        return isSeller(memberId) ? this.sellerLastReadMessageId : this.buyerLastReadMessageId;
    }
}