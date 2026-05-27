package com.reservation.tablereservationservice.application.reservation.scheduler;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacity;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.global.config.ReservationStreamProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisReconciliationScheduler {

	private final DailySlotCapacityRepository dailySlotCapacityRepository;
	private final StringRedisTemplate redisTemplate;
	private final ReservationStreamProperties streamProperties;

	@Scheduled(cron = "0 0 3 * * *") // 매일 새벽 3시
	public void syncCapacityToRedis() {
		List<DailySlotCapacity> capacities = dailySlotCapacityRepository.findAllFromDate(LocalDate.now());
		if (capacities.isEmpty()) {
			return;
		}

		for (DailySlotCapacity capacity : capacities) {
			String key = streamProperties.remainingKey(capacity.getSlotId(), capacity.getDate().toString());
			redisTemplate.opsForValue().set(key, String.valueOf(capacity.getRemainingCount()), 7, TimeUnit.DAYS);
		}

		log.info("[REDIS_RECONCILE] Redis 좌석 재동기화 완료 count={}", capacities.size());
	}
}
