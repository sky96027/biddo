package com.biddo.infra.redis;

import com.biddo.domain.auction.service.AuctionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RedisKeyExpirationListenerTest {

    @InjectMocks
    private RedisKeyExpirationListener listener;

    @Mock
    private AuctionService auctionService;

    @Test
    @DisplayName("TTL 만료 - 경매 활성화 키 처리")
    void handleExpiredKey_startKey_activatesAuction() {
        // given
        String expiredKey = "auction:start:1";

        // when
        listener.handleExpiredKey(expiredKey);

        // then
        verify(auctionService).activateAuction(1L);
        verify(auctionService, never()).endAuction(1L);
    }

    @Test
    @DisplayName("TTL 만료 - 경매 종료 키 처리")
    void handleExpiredKey_endKey_endsAuction() {
        // given
        String expiredKey = "auction:end:42";

        // when
        listener.handleExpiredKey(expiredKey);

        // then
        verify(auctionService).endAuction(42L);
        verify(auctionService, never()).activateAuction(42L);
    }

    @Test
    @DisplayName("TTL 만료 - 관련 없는 키는 무시")
    void handleExpiredKey_unrelatedKey_ignored() {
        // given
        String expiredKey = "session:abc123";

        // when
        listener.handleExpiredKey(expiredKey);

        // then
        verifyNoInteractions(auctionService);
    }

    @Test
    @DisplayName("TTL 만료 - 예외 발생 시 전파하지 않음")
    void handleExpiredKey_exceptionThrown_doesNotPropagate() {
        // given
        String expiredKey = "auction:end:invalid";

        // when - NumberFormatException 발생하지만 catch됨
        listener.handleExpiredKey(expiredKey);

        // then - 예외가 전파되지 않으면 테스트 통과
    }
}