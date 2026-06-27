package com.biddo.infra.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SchedulerLockExecutor")
class SchedulerLockExecutorTest {

    @InjectMocks
    private SchedulerLockExecutor executor;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @Test
    @DisplayName("락 획득 성공 시 액션을 실행하고 락을 해제한다")
    void tryExecuteWithLock_lockAcquired_executesActionAndUnlocks() throws InterruptedException {
        // given
        given(redissonClient.getLock("scheduler:lock:test")).willReturn(rLock);
        given(rLock.tryLock(0, 55L, TimeUnit.SECONDS)).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);
        Runnable action = mock(Runnable.class);

        // when
        executor.tryExecuteWithLock("scheduler:lock:test", 55L, action);

        // then
        verify(action).run();
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("락 획득 실패 시 액션을 실행하지 않는다")
    void tryExecuteWithLock_lockNotAcquired_skipsAction() throws InterruptedException {
        // given
        given(redissonClient.getLock("scheduler:lock:test")).willReturn(rLock);
        given(rLock.tryLock(0, 55L, TimeUnit.SECONDS)).willReturn(false);
        Runnable action = mock(Runnable.class);

        // when
        executor.tryExecuteWithLock("scheduler:lock:test", 55L, action);

        // then
        verify(action, never()).run();
        verify(rLock, never()).unlock();
    }

    @Test
    @DisplayName("InterruptedException 발생 시 액션을 실행하지 않고 인터럽트 플래그를 복원한다")
    void tryExecuteWithLock_interrupted_restoresInterruptFlagAndSkipsAction() throws InterruptedException {
        // given
        given(redissonClient.getLock("scheduler:lock:test")).willReturn(rLock);
        given(rLock.tryLock(0, 55L, TimeUnit.SECONDS)).willThrow(new InterruptedException());
        Runnable action = mock(Runnable.class);

        // when
        executor.tryExecuteWithLock("scheduler:lock:test", 55L, action);

        // then
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(action, never()).run();
        Thread.interrupted(); // 다른 테스트를 위해 플래그 초기화
    }

    @Test
    @DisplayName("액션 실행 중 예외가 발생해도 락을 해제한다")
    void tryExecuteWithLock_actionThrows_unlocksAnyway() throws InterruptedException {
        // given
        given(redissonClient.getLock("scheduler:lock:test")).willReturn(rLock);
        given(rLock.tryLock(0, 55L, TimeUnit.SECONDS)).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);
        Runnable action = mock(Runnable.class);
        doThrow(new RuntimeException("action failed")).when(action).run();

        // when
        try {
            executor.tryExecuteWithLock("scheduler:lock:test", 55L, action);
        } catch (RuntimeException ignored) {
        }

        // then
        verify(rLock).unlock();
    }
}