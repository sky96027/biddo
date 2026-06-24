package com.biddo.domain.notification.port.out;

import com.biddo.domain.notification.entity.Notification;

public interface NotificationPushPort {

    void push(Long receiverId, Notification notification);
}
