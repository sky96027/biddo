package com.biddo.infra.notification;

import com.biddo.domain.notification.model.Notification;
import com.biddo.domain.notification.port.out.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationJpaRepository jpaRepository;

    @Override
    public Notification save(Notification notification) {
        return jpaRepository.save(notification);
    }

    @Override
    public Optional<Notification> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Notification> findByReceiverIdFirstPage(Long receiverId, int size) {
        return jpaRepository.findByReceiverIdFirstPage(receiverId, PageRequest.of(0, size));
    }

    @Override
    public List<Notification> findByReceiverIdWithCursor(Long receiverId, Long cursor, int size) {
        return jpaRepository.findByReceiverIdWithCursor(receiverId, cursor, PageRequest.of(0, size));
    }

    @Override
    public List<Notification> findUnreadByReceiverIdFirstPage(Long receiverId, int size) {
        return jpaRepository.findUnreadByReceiverIdFirstPage(receiverId, PageRequest.of(0, size));
    }

    @Override
    public List<Notification> findUnreadByReceiverIdWithCursor(Long receiverId, Long cursor, int size) {
        return jpaRepository.findUnreadByReceiverIdWithCursor(receiverId, cursor, PageRequest.of(0, size));
    }

    @Override
    public List<Notification> findByReceiverIdAfter(Long receiverId, Long lastEventId) {
        return jpaRepository.findByReceiverIdAfter(receiverId, lastEventId);
    }
}
