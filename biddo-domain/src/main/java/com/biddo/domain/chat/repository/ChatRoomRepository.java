package com.biddo.domain.chat.repository;

import com.biddo.domain.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    @Query("SELECT cr FROM ChatRoom cr JOIN FETCH cr.auction JOIN FETCH cr.seller JOIN FETCH cr.buyer " +
            "WHERE cr.seller.id = :memberId OR cr.buyer.id = :memberId ORDER BY cr.createdAt DESC")
    List<ChatRoom> findByMemberId(@Param("memberId") Long memberId);

    @Query("SELECT cr FROM ChatRoom cr JOIN FETCH cr.seller JOIN FETCH cr.buyer WHERE cr.id = :id")
    Optional<ChatRoom> findByIdWithMembers(@Param("id") Long id);

    boolean existsByAuctionId(Long auctionId);
}