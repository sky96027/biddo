package com.biddo.domain.notification.service;

import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.notification.entity.Notification;
import com.biddo.domain.notification.entity.NotificationType;
import com.biddo.domain.notification.exception.NotificationErrorCode;
import com.biddo.domain.notification.port.NotificationPushPort;
import com.biddo.domain.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPushPort notificationPushPort;

    private Member receiver;
    private Notification notification;

    @BeforeEach
    void setUp() {
        receiver = Member.builder().email("user@test.com").password("encoded").nickname("user").build();
        setId(receiver, 1L);

        notification = Notification.builder()
                .receiver(receiver)
                .auctionId(100L)
                .type(NotificationType.OUTBID)
                .message("다른 사용자가 더 높은 금액을 입찰했습니다.")
                .build();
        setId(notification, 1L);
    }

    @Test
    @DisplayName("알림 생성 성공")
    void create_success() {
        given(notificationRepository.save(any(Notification.class))).willAnswer(inv -> {
            Notification n = inv.getArgument(0);
            setId(n, 1L);
            return n;
        });

        Notification result = notificationService.create(receiver, 100L, NotificationType.OUTBID, "입찰이 추월되었습니다.");

        assertThat(result.getReceiver().getId()).isEqualTo(1L);
        assertThat(result.getAuctionId()).isEqualTo(100L);
        assertThat(result.getType()).isEqualTo(NotificationType.OUTBID);
        assertThat(result.isRead()).isFalse();
        verify(notificationRepository).save(any(Notification.class));
        verify(notificationPushPort).push(eq(1L), any(Notification.class));
    }

    @Test
    @DisplayName("알림 목록 조회 - 전체 (첫 페이지)")
    void findByReceiverId_allFirstPage() {
        given(notificationRepository.findByReceiverIdFirstPage(eq(1L), any(PageRequest.class)))
                .willReturn(List.of(notification));

        List<Notification> result = notificationService.findByReceiverId(1L, null, null, 20);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("알림 목록 조회 - 안읽은 것만 (첫 페이지)")
    void findByReceiverId_unreadFirstPage() {
        given(notificationRepository.findUnreadByReceiverIdFirstPage(eq(1L), any(PageRequest.class)))
                .willReturn(List.of(notification));

        List<Notification> result = notificationService.findByReceiverId(1L, false, null, 20);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("알림 목록 조회 - 커서 기반")
    void findByReceiverId_withCursor() {
        given(notificationRepository.findByReceiverIdWithCursor(eq(1L), eq(10L), any(PageRequest.class)))
                .willReturn(List.of(notification));

        List<Notification> result = notificationService.findByReceiverId(1L, null, 10L, 20);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("알림 목록 조회 - 안읽은 것만 + 커서")
    void findByReceiverId_unreadWithCursor() {
        given(notificationRepository.findUnreadByReceiverIdWithCursor(eq(1L), eq(10L), any(PageRequest.class)))
                .willReturn(List.of(notification));

        List<Notification> result = notificationService.findByReceiverId(1L, false, 10L, 20);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("읽음 처리 성공")
    void markAsRead_success() {
        given(notificationRepository.findById(1L)).willReturn(Optional.of(notification));

        notificationService.markAsRead(1L, 1L);

        assertThat(notification.isRead()).isTrue();
    }

    @Test
    @DisplayName("읽음 처리 실패 - 알림 미존재")
    void markAsRead_notFound() {
        given(notificationRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
    }

    @Test
    @DisplayName("읽음 처리 실패 - 본인 알림 아님")
    void markAsRead_notOwner() {
        given(notificationRepository.findById(1L)).willReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(NotificationErrorCode.NOT_NOTIFICATION_OWNER));
    }

    private void setId(Object entity, Long id) {
        try {
            java.lang.reflect.Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}