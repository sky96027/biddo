package com.biddo.domain.notification.service;

import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.repository.MemberRepository;
import com.biddo.domain.notification.entity.PriceAlert;
import com.biddo.domain.notification.exception.NotificationErrorCode;
import com.biddo.domain.notification.repository.PriceAlertRepository;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PriceAlertServiceTest {

    @InjectMocks
    private PriceAlertService priceAlertService;

    @Mock
    private PriceAlertRepository priceAlertRepository;

    @Mock
    private MemberRepository memberRepository;

    private Member member;
    private PriceAlert priceAlert;

    @BeforeEach
    void setUp() {
        member = Member.builder().email("user@test.com").password("encoded").nickname("user").build();
        setId(member, 1L);

        priceAlert = PriceAlert.builder()
                .member(member)
                .auctionId(100L)
                .thresholdPercent(10)
                .basePrice(50000L)
                .build();
        setId(priceAlert, 1L);
    }

    @Test
    @DisplayName("가격 알림 생성 성공")
    void create_success() {
        given(priceAlertRepository.existsByMemberIdAndAuctionId(1L, 100L)).willReturn(false);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(priceAlertRepository.save(any(PriceAlert.class))).willAnswer(inv -> {
            PriceAlert pa = inv.getArgument(0);
            setId(pa, 1L);
            return pa;
        });

        PriceAlert result = priceAlertService.create(1L, 100L, 10, 50000L);

        assertThat(result.getAuctionId()).isEqualTo(100L);
        assertThat(result.getThresholdPercent()).isEqualTo(10);
        assertThat(result.getBasePrice()).isEqualTo(50000L);
        assertThat(result.isActive()).isTrue();
    }

    @Test
    @DisplayName("가격 알림 생성 실패 - 중복")
    void create_duplicate() {
        given(priceAlertRepository.existsByMemberIdAndAuctionId(1L, 100L)).willReturn(true);

        assertThatThrownBy(() -> priceAlertService.create(1L, 100L, 10, 50000L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(NotificationErrorCode.PRICE_ALERT_DUPLICATE));
    }

    @Test
    @DisplayName("가격 알림 목록 조회")
    void findByMemberId() {
        given(priceAlertRepository.findByMemberIdOrderByIdDesc(1L)).willReturn(List.of(priceAlert));

        List<PriceAlert> result = priceAlertService.findByMemberId(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("가격 알림 임계값 수정 성공")
    void update_success() {
        given(priceAlertRepository.findById(1L)).willReturn(Optional.of(priceAlert));

        PriceAlert result = priceAlertService.update(1L, 1L, 20);

        assertThat(result.getThresholdPercent()).isEqualTo(20);
    }

    @Test
    @DisplayName("가격 알림 수정 실패 - 본인 아님")
    void update_notOwner() {
        given(priceAlertRepository.findById(1L)).willReturn(Optional.of(priceAlert));

        assertThatThrownBy(() -> priceAlertService.update(1L, 999L, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(NotificationErrorCode.NOT_PRICE_ALERT_OWNER));
    }

    @Test
    @DisplayName("가격 알림 토글 - 비활성화")
    void toggleActive_deactivate() {
        given(priceAlertRepository.findById(1L)).willReturn(Optional.of(priceAlert));

        priceAlertService.toggleActive(1L, 1L);

        assertThat(priceAlert.isActive()).isFalse();
    }

    @Test
    @DisplayName("가격 알림 토글 - 활성화")
    void toggleActive_activate() {
        priceAlert.deactivate();
        given(priceAlertRepository.findById(1L)).willReturn(Optional.of(priceAlert));

        priceAlertService.toggleActive(1L, 1L);

        assertThat(priceAlert.isActive()).isTrue();
    }

    @Test
    @DisplayName("가격 알림 삭제 성공")
    void delete_success() {
        given(priceAlertRepository.findById(1L)).willReturn(Optional.of(priceAlert));

        priceAlertService.delete(1L, 1L);

        verify(priceAlertRepository).delete(priceAlert);
    }

    @Test
    @DisplayName("가격 알림 삭제 실패 - 미존재")
    void delete_notFound() {
        given(priceAlertRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> priceAlertService.delete(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(NotificationErrorCode.PRICE_ALERT_NOT_FOUND));
    }

    @Test
    @DisplayName("PriceAlert.isTriggered - 임계값 이상 상승 시 true")
    void isTriggered_true() {
        assertThat(priceAlert.isTriggered(55000L)).isTrue(); // 10% 상승
    }

    @Test
    @DisplayName("PriceAlert.isTriggered - 임계값 미만 상승 시 false")
    void isTriggered_false() {
        assertThat(priceAlert.isTriggered(54000L)).isFalse(); // 8% 상승
    }

    @Test
    @DisplayName("PriceAlert.isTriggered - 비활성 상태 시 false")
    void isTriggered_inactive() {
        priceAlert.deactivate();
        assertThat(priceAlert.isTriggered(60000L)).isFalse();
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