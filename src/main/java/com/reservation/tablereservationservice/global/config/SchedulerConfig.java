package com.reservation.tablereservationservice.global.config;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.reservation.tablereservationservice.application.reservation.scheduler.PendingReservationExpireScheduler;
import com.reservation.tablereservationservice.application.reservation.scheduler.RedisReconciliationScheduler;
import com.reservation.tablereservationservice.infrastructure.outbox.OutboxPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulerConfig {

	private final OutboxPublisher outboxPublisher;
	private final PendingReservationExpireScheduler reservationExpireScheduler;
	private final RedisReconciliationScheduler redisReconciliationScheduler;

	@Bean
	public ThreadPoolTaskScheduler taskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(4);
		scheduler.setThreadNamePrefix("scheduling-");
		scheduler.setWaitForTasksToCompleteOnShutdown(true);
		scheduler.setAwaitTerminationSeconds(20);
		return scheduler;
	}

	/**
	 * outbox 미발행 취소 메시지를 큐로 발행 (1초 주기)
	 */
	@Scheduled(fixedDelay = 1000)
	public void publishOutbox() {
		LocalDateTime start = LocalDateTime.now();

		try {
			log.debug("===== [publishOutbox] START =====");
			outboxPublisher.publishPending();
		} finally {
			LocalDateTime end = LocalDateTime.now();
			log.debug("===== [publishOutbox] END (elapsed {}ms) =====", Duration.between(start, end).toMillis());
		}
	}

	/**
	 * 오래된 PUBLISHED outbox 레코드 정리 (매주 일요일 04:00)
	 */
	@Scheduled(cron = "0 0 4 * * SUN")
	public void cleanupOutbox() {
		LocalDateTime start = LocalDateTime.now();

		try {
			log.debug("===== [cleanupOutbox] START =====");
			outboxPublisher.cleanupPublished();
		} finally {
			LocalDateTime end = LocalDateTime.now();
			log.debug("===== [cleanupOutbox] END (elapsed {}ms) =====", Duration.between(start, end).toMillis());
		}
	}

	/**
	 * PENDING 예약 만료 처리·결제 재조회 복구 (60초 주기)
	 */
	@Scheduled(fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
	public void expirePendingReservations() {
		LocalDateTime start = LocalDateTime.now();

		try {
			log.debug("===== [expirePendingReservations] START =====");
			reservationExpireScheduler.expirePendingReservations();
		} finally {
			LocalDateTime end = LocalDateTime.now();
			log.debug("===== [expirePendingReservations] END (elapsed {}ms) =====", Duration.between(start, end).toMillis());
		}
	}

	/**
	 * DB 잔여수량을 Redis로 동기화 (매일 03:00)
	 */
	@Scheduled(cron = "0 0 3 * * *")
	public void reconcileRedisCapacity() {
		LocalDateTime start = LocalDateTime.now();

		try {
			log.debug("===== [reconcileRedisCapacity] START =====");
			redisReconciliationScheduler.syncCapacityToRedis();
		} finally {
			LocalDateTime end = LocalDateTime.now();
			log.debug("===== [reconcileRedisCapacity] END (elapsed {}ms) =====", Duration.between(start, end).toMillis());
		}
	}
}
