package com.reservation.tablereservationservice.application.reservation.aspect;

import java.time.Duration;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.tablereservationservice.application.reservation.service.ReservationCreateResult;
import com.reservation.tablereservationservice.application.reservation.service.ReservationIdempotencyCache;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Aspect
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class ReservationIdempotencyAspect {

	private static final String PREFIX = "reservation:idempotency:";
	private static final Duration PROCESSING_TTL = Duration.ofSeconds(30);
	private static final Duration RESULT_TTL = Duration.ofHours(24);

	private final StringRedisTemplate redisTemplate;
	private final ReservationRepository reservationRepository;
	private final RestaurantSlotRepository restaurantSlotRepository;
	private final ObjectMapper objectMapper;

	@Around("execution(* com.reservation.tablereservationservice.application.reservation.service.ReservationService.reserve(..)) && args(email, requestDto, idempotencyKey)")
	public Object around(ProceedingJoinPoint joinPoint, String email, ReservationRequestDto requestDto, String idempotencyKey) throws Throwable {
		String redisKey = PREFIX + idempotencyKey;

		Boolean acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", PROCESSING_TTL);

		if (Boolean.FALSE.equals(acquired)) {
			String cached = redisTemplate.opsForValue().get(redisKey);
			if (cached != null && !"PROCESSING".equals(cached)) {
				return buildFromCache(cached);
			}
			// PROCESSING 상태(동시 진입) — 서비스까지 진행시켜 DataIntegrityViolationException 안전망으로 처리
			log.warn("[IDEMPOTENCY] 동시 요청 감지 idempotencyKey={}", idempotencyKey);
		}

		try {
			ReservationCreateResult result = (ReservationCreateResult)joinPoint.proceed();
			cacheResult(redisKey, result);
			return result;
		} catch (DataIntegrityViolationException e) {
			// 트랜잭션 rollback 완료 → runOnRollback이 Redis 좌석 복원 처리
			// 먼저 성공한 요청의 예약을 idempotency key로 조회해 반환
			return resolveFromDb(redisKey, idempotencyKey);
		} catch (Throwable t) {
			redisTemplate.delete(redisKey);
			throw t;
		}
	}

	private ReservationCreateResult resolveFromDb(String redisKey, String idempotencyKey) {
		Reservation reservation = reservationRepository.findByIdempotencyKey(idempotencyKey)
				.orElseThrow(() -> new ReservationException(ErrorCode.RESERVATION_CONCURRENCY_ERROR));
		RestaurantSlot slot = restaurantSlotRepository.fetchById(reservation.getSlotId());
		ReservationCreateResult result = new ReservationCreateResult(reservation, slot.depositAmount(reservation.getPartySize()));
		cacheResult(redisKey, result);
		return result;
	}

	private ReservationCreateResult buildFromCache(String cached) {
		try {
			ReservationIdempotencyCache dto = objectMapper.readValue(cached, ReservationIdempotencyCache.class);
			Reservation reservation = reservationRepository.fetchById(dto.reservationId());
			RestaurantSlot slot = restaurantSlotRepository.fetchById(reservation.getSlotId());
			return new ReservationCreateResult(reservation, slot.depositAmount(reservation.getPartySize()));
		} catch (Exception e) {
			log.error("[IDEMPOTENCY] 캐시 역직렬화 실패", e);
			throw new ReservationException(ErrorCode.RESERVATION_CONCURRENCY_ERROR);
		}
	}

	private void cacheResult(String redisKey, ReservationCreateResult result) {
		try {
			ReservationIdempotencyCache dto = new ReservationIdempotencyCache(
					result.reservation().getReservationId(),
					result.depositAmount()
			);
			redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(dto), RESULT_TTL);
		} catch (Exception e) {
			log.warn("[IDEMPOTENCY] 캐시 저장 실패 (best-effort)", e);
		}
	}
}
