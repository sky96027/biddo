package com.biddo.domain.notification.port.out;

import com.biddo.domain.notification.model.Notification;

public interface NotificationPushPort {

    void push(Long receiverId, Notification notification);
}
