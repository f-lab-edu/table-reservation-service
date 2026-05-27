package com.reservation.tablereservationservice.infrastructure.redis;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacity;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.global.config.ReservationStreamProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(1) // Consumer 구독(Order=2) 보다 먼저 실행
public class RedisRemainingCapacityInitializer implements ApplicationRunner {

	private final DailySlotCapacityRepository dailySlotCapacityRepository;
	private final StringRedisTemplate redisTemplate;
	private final ReservationStreamProperties streamProperties;

	/**
	 * 앱 시작 시 오늘 이후의 모든 daily_slot_capacity를 Redis에 적재한다.
	 */
	@Override
	public void run(ApplicationArguments args) {
		LocalDate today = LocalDate.now();
		List<DailySlotCapacity> capacities = dailySlotCapacityRepository.findAllFromDate(today);

		for (DailySlotCapacity capacity : capacities) {
			String key = streamProperties.remainingKey(capacity.getSlotId(), capacity.getDate().toString());
			String value = String.valueOf(capacity.getRemainingCount());
			redisTemplate.opsForValue().set(key, value);
		}

		log.info("[RedisInit] 잔여 좌석 초기화 완료: {}건 (기준일={})", capacities.size(), today);
	}
}
