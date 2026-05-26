package com.reservation.tablereservationservice.infrastructure.redis;

import java.time.LocalDate;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.reservation.tablereservationservice.global.config.ReservationStreamProperties;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationPublisher {

	// Lua 스크립트 결과 상태 코드 정의
	private static final long RESULT_SLOT_NOT_OPENED = -2L;
	private static final long RESULT_CAPACITY_NOT_ENOUGH = -1L;

	private final StringRedisTemplate redisTemplate;
	private final ReservationStreamProperties streamProperties;

	private RedisScript<Long> holdScript;

	@PostConstruct
	void loadScript() {
		holdScript = RedisScript.of(new ClassPathResource("scripts/seat_decr.lua"), Long.class);
	}

	public void holdSeat(Long slotId, LocalDate date, int partySize) {
		String remainingKey = streamProperties.remainingKey(slotId, date.toString());
		long result = redisTemplate.execute(holdScript, List.of(remainingKey), String.valueOf(partySize));

		if (result == RESULT_SLOT_NOT_OPENED) {
			throw new ReservationException(ErrorCode.RESERVATION_SLOT_NOT_OPENED);
		}
		if (result == RESULT_CAPACITY_NOT_ENOUGH) {
			throw new ReservationException(ErrorCode.RESERVATION_CAPACITY_NOT_ENOUGH);
		}
	}

	public void releaseSeat(Long slotId, LocalDate date, int partySize) {
		String remainingKey = streamProperties.remainingKey(slotId, date.toString());
		try {
			redisTemplate.opsForValue().increment(remainingKey, partySize);
			log.info("[SEAT_RESTORE] Redis 좌석 복원 slotId={}, date={}, partySize={}", slotId, date, partySize);
		} catch (Exception e) {
			log.warn("[SEAT_RESTORE] Redis 좌석 복원 실패 (best-effort) slotId={}, date={}", slotId, date, e);
		}
	}
}
