package com.reservation.tablereservationservice.infrastructure.outbox;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class SchedulerThreadContentionTest {

	private static final long SLOW_TASK_MILLIS = 300; // 느린 작업이 스레드를 붙잡는 시간

	@Test
	@DisplayName("스레드 1개: 느린 작업 때문에 다른 스케줄러가 늦어진다")
	void singleThread_slowTask_delaysPublisher() throws Exception {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(1);
		scheduler.initialize();
		try {
			CountDownLatch publisherStarted = new CountDownLatch(1);
			AtomicLong publisherStartedAt = new AtomicLong();
			long baseline = System.currentTimeMillis();

			// 느린 작업 먼저 올려 스레드를 붙잡게 한다
			scheduler.schedule(() -> sleep(SLOW_TASK_MILLIS), Instant.now());
			// 곧이어 두 번째 작업을 올린다
			scheduler.schedule(() -> {
				publisherStartedAt.set(System.currentTimeMillis());
				publisherStarted.countDown();
			}, Instant.now().plusMillis(10));

			assertThat(publisherStarted.await(2, TimeUnit.SECONDS)).isTrue();
			long publisherDelayMs = publisherStartedAt.get() - baseline;

			// 두 번째 작업은 느린 작업이 끝날 때까지(약 300ms) 시작도 못 한다
			assertThat(publisherDelayMs).isGreaterThanOrEqualTo(SLOW_TASK_MILLIS - 50);
		} finally {
			scheduler.shutdown();
		}
	}

	private void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
