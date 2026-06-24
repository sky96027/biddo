package com.biddo.domain.notification.service;

import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.notification.exception.NotificationErrorCode;
import com.biddo.domain.notification.model.Notification;
import com.biddo.domain.notification.model.NotificationType;
import com.biddo.domain.notification.port.out.NotificationPushPort;
import com.biddo.domain.notification.port.out.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPushPort notificationPushPort;

    @Transactional
    public Notification create(Member receiver, Long auctionId, NotificationType type, String message) {
        Notification notification = Notification.builder()
                .receiver(receiver)
                .auctionId(auctionId)
                .type(type)
                .message(message)
                .build();
        Notification saved = notificationRepository.save(notification);
        notificationPushPort.push(receiver.getId(), saved);
        return saved;
    }

    public List<Notification> findByReceiverId(Long receiverId, Boolean isRead, Long cursor, int size) {
        if (Boolean.FALSE.equals(isRead)) {
            if (cursor == null) {
                return notificationRepository.findUnreadByReceiverIdFirstPage(receiverId, size);
            }
            return notificationRepository.findUnreadByReceiverIdWithCursor(receiverId, cursor, size);
        }

        if (cursor == null) {
            return notificationRepository.findByReceiverIdFirstPage(receiverId, size);
        }
        return notificationRepository.findByReceiverIdWithCursor(receiverId, cursor, size);
    }

    public List<Notification> findAfter(Long receiverId, Long lastEventId) {
        return notificationRepository.findByReceiverIdAfter(receiverId, lastEventId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long memberId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.isOwner(memberId)) {
            throw new BusinessException(NotificationErrorCode.NOT_NOTIFICATION_OWNER);
        }

        notification.markAsRead();
    }
}
