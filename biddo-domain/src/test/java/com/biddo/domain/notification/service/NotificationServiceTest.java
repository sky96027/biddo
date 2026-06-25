package com.biddo.domain.notification.service;

import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.notification.exception.NotificationErrorCode;
import com.biddo.domain.notification.entity.Notification;
import com.biddo.domain.notification.entity.NotificationType;
import com.biddo.domain.notification.port.out.NotificationPushPort;
import com.biddo.domain.notification.port.out.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
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
    void findByReceiverId_allFirstPage_returnsResult() {
        given(notificationRepository.findByReceiverIdFirstPage(eq(1L), eq(20)))
                .willReturn(List.of(notification));

        List<Notification> result = notificationService.findByReceiverId(1L, null, null, 20);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("알림 목록 조회 - 안읽은 것만 (첫 페이지)")
    void findByReceiverId_unreadFirstPage_returnsResult() {
        given(notificationRepository.findUnreadByReceiverIdFirstPage(eq(1L), eq(20)))
                .willReturn(List.of(notification));

        List<Notification> result = notificationService.findByReceiverId(1L, false, null, 20);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("알림 목록 조회 - 커서 기반")
    void findByReceiverId_withCursor_returnsResult() {
        given(notificationRepository.findByReceiverIdWithCursor(eq(1L), eq(10L), eq(20)))
                .willReturn(List.of(notification));

        List<Notification> result = notificationService.findByReceiverId(1L, null, 10L, 20);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("알림 목록 조회 - 안읽은 것만 + 커서")
    void findByReceiverId_unreadWithCursor_returnsResult() {
        given(notificationRepository.findUnreadByReceiverIdWithCursor(eq(1L), eq(10L), eq(20)))
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
    void markAsRead_notFound_throwsException() {
        given(notificationRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
    }

    @Test
    @DisplayName("배치 알림 생성 성공 - 전체 saveAll + 각각 push")
    void createAll_success() {
        // given
        Member receiver2 = Member.builder().email("user2@test.com").password("encoded").nickname("user2").build();
        setId(receiver2, 2L);

        List<NotificationService.NotificationSpec> specs = List.of(
                new NotificationService.NotificationSpec(receiver, 100L, NotificationType.BID, "입찰 알림"),
                new NotificationService.NotificationSpec(receiver2, 100L, NotificationType.OUTBID, "추월 알림")
        );

        Notification n1 = Notification.builder().receiver(receiver).auctionId(100L)
                .type(NotificationType.BID).message("입찰 알림").build();
        setId(n1, 1L);
        Notification n2 = Notification.builder().receiver(receiver2).auctionId(100L)
                .type(NotificationType.OUTBID).message("추월 알림").build();
        setId(n2, 2L);

        given(notificationRepository.saveAll(any())).willReturn(List.of(n1, n2));

        // when
        notificationService.createAll(specs);

        // then
        verify(notificationRepository).saveAll(any());
        verify(notificationPushPort).push(eq(1L), eq(n1));
        verify(notificationPushPort).push(eq(2L), eq(n2));
    }

    @Test
    @DisplayName("배치 알림 생성 - 빈 목록이면 saveAll 호출 없음")
    void createAll_emptySpecs_noSave() {
        // when
        notificationService.createAll(List.of());

        // then
        verify(notificationRepository, never()).saveAll(any());
        verify(notificationPushPort, never()).push(any(), any());
    }

    @Test
    @DisplayName("읽음 처리 실패 - 본인 알림 아님")
    void markAsRead_notOwner_throwsException() {
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