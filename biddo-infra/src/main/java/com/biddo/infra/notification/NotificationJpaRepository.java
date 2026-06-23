package com.biddo.infra.notification;

import com.biddo.domain.notification.model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationJpaRepository extends JpaRepository<Notification, Long> {

    @Query("SELECT n FROM Notification n WHERE n.receiver.id = :receiverId ORDER BY n.id DESC")
    List<Notification> findByReceiverIdFirstPage(@Param("receiverId") Long receiverId, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.receiver.id = :receiverId AND n.id < :cursor ORDER BY n.id DESC")
    List<Notification> findByReceiverIdWithCursor(@Param("receiverId") Long receiverId,
                                                  @Param("cursor") Long cursor,
                                                  Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.receiver.id = :receiverId AND n.isRead = false ORDER BY n.id DESC")
    List<Notification> findUnreadByReceiverIdFirstPage(@Param("receiverId") Long receiverId, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.receiver.id = :receiverId AND n.isRead = false AND n.id < :cursor ORDER BY n.id DESC")
    List<Notification> findUnreadByReceiverIdWithCursor(@Param("receiverId") Long receiverId,
                                                        @Param("cursor") Long cursor,
                                                        Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.receiver.id = :receiverId AND n.id > :lastEventId ORDER BY n.id ASC")
    List<Notification> findByReceiverIdAfter(@Param("receiverId") Long receiverId,
                                             @Param("lastEventId") Long lastEventId);
}
