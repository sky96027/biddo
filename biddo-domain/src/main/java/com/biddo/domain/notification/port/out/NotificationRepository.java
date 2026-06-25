package com.biddo.domain.notification.port.out;

import com.biddo.domain.notification.entity.Notification;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository {

    Notification save(Notification notification);

    List<Notification> saveAll(List<Notification> notifications);

    Optional<Notification> findById(Long id);

    List<Notification> findByReceiverIdFirstPage(Long receiverId, int size);

    List<Notification> findByReceiverIdWithCursor(Long receiverId, Long cursor, int size);

    List<Notification> findUnreadByReceiverIdFirstPage(Long receiverId, int size);

    List<Notification> findUnreadByReceiverIdWithCursor(Long receiverId, Long cursor, int size);

    List<Notification> findByReceiverIdAfter(Long receiverId, Long lastEventId);
}
