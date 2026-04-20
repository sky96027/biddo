package com.biddo.domain.notification.service;

import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.notification.entity.Notification;
import com.biddo.domain.notification.entity.NotificationType;
import com.biddo.domain.notification.exception.NotificationErrorCode;
import com.biddo.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public Notification create(Member receiver, Long auctionId, NotificationType type, String message) {
        Notification notification = Notification.builder()
                .receiver(receiver)
                .auctionId(auctionId)
                .type(type)
                .message(message)
                .build();
        return notificationRepository.save(notification);
    }

    public List<Notification> findByReceiverId(Long receiverId, Boolean isRead, Long cursor, int size) {
        PageRequest pageRequest = PageRequest.of(0, size);

        if (Boolean.FALSE.equals(isRead)) {
            if (cursor == null) {
                return notificationRepository.findUnreadByReceiverIdFirstPage(receiverId, pageRequest);
            }
            return notificationRepository.findUnreadByReceiverIdWithCursor(receiverId, cursor, pageRequest);
        }

        if (cursor == null) {
            return notificationRepository.findByReceiverIdFirstPage(receiverId, pageRequest);
        }
        return notificationRepository.findByReceiverIdWithCursor(receiverId, cursor, pageRequest);
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