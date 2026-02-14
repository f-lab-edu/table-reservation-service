package com.reservation.tablereservationservice.application.reservation.facade;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.RetryContext;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Component;

import com.reservation.tablereservationservice.application.reservation.service.ReservationService;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationOptimisticFacade {

	private final ReservationService reservationService;

	@Retryable(
		retryFor = {OptimisticLockingFailureException.class, ObjectOptimisticLockingFailureException.class},
		maxAttempts = 3,
		backoff = @Backoff(delay = 20, multiplier = 2.0, maxDelay = 200, random = true)
	)
	public Reservation createWithRetry(String email, ReservationRequestDto requestDto, long serverReceivedSeq) {
		int attempt = getCurrentAttempt();

		if (attempt == 1) {
			log.info(
				"[OPT-LOCK] first attempt (seq={}, reqId={}, email={}, slotId={}, date={}, partySize={})",
				serverReceivedSeq,
				requestDto.getRequestId(),
				email,
				requestDto.getSlotId(),
				requestDto.getDate(),
				requestDto.getPartySize()
			);
		} else {
			log.warn(
				"[OPT-LOCK] retry attempt={} (seq={}, reqId={}, email={})",
				attempt,
				serverReceivedSeq,
				requestDto.getRequestId(),
				email
			);
		}

		return reservationService.create(email, requestDto, serverReceivedSeq);
	}

	@Recover
	public Reservation recover(
		OptimisticLockingFailureException e,
		String email,
		ReservationRequestDto requestDto,
		long serverReceivedSeq
	) {

		int finalAttempt = getCurrentAttempt();

		log.error(
			"[OPT-LOCK] retry exhausted (attempt={}, seq={}, reqId={}, email={}, cause={})",
			finalAttempt, serverReceivedSeq, requestDto.getRequestId(), email, e.getClass().getSimpleName(), e
		);

		throw new ReservationException(ErrorCode.RESERVATION_CONCURRENCY_ERROR, "재시도 횟수 초과");
	}

	@Recover
	public Reservation recover(
		ReservationException e,
		String email,
		ReservationRequestDto requestDto,
		long serverReceivedSeq
	) {
		// 좌석 부족 등은 정상 비즈니스 실패 처리
		throw e;
	}

	private int getCurrentAttempt() {
		RetryContext context = RetrySynchronizationManager.getContext();
		if (context == null) {
			return 1;
		}
		return context.getRetryCount() + 1;
	}
}
