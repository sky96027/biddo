package com.biddo.domain.chat.repository;

import com.biddo.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT cm FROM ChatMessage cm JOIN FETCH cm.sender WHERE cm.chatRoom.id = :roomId AND cm.id < :cursor ORDER BY cm.id DESC")
    List<ChatMessage> findByRoomIdWithCursor(@Param("roomId") Long roomId,
                                             @Param("cursor") Long cursor,
                                             Pageable pageable);

    @Query("SELECT cm FROM ChatMessage cm JOIN FETCH cm.sender WHERE cm.chatRoom.id = :roomId ORDER BY cm.id DESC")
    List<ChatMessage> findByRoomIdFirstPage(@Param("roomId") Long roomId, Pageable pageable);

    @Query("SELECT cm FROM ChatMessage cm WHERE cm.chatRoom.id = :roomId ORDER BY cm.id DESC LIMIT 1")
    Optional<ChatMessage> findLatestByRoomId(@Param("roomId") Long roomId);

    long countByChatRoomIdAndIdGreaterThan(Long chatRoomId, Long messageId);

    @Query("SELECT cm FROM ChatMessage cm WHERE cm.id IN (SELECT MAX(cm2.id) FROM ChatMessage cm2 WHERE cm2.chatRoom.id IN :roomIds GROUP BY cm2.chatRoom.id)")
    List<ChatMessage> findLatestByRoomIds(@Param("roomIds") List<Long> roomIds);
}