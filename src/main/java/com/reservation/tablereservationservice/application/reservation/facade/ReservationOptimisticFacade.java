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
	public Reservation cancelWithRetry(String email, Long reservationId) {
		int attempt = getCurrentAttempt();

		if (attempt == 1) {
			log.info("[OPT-LOCK] cancel attempt (email={}, reservationId={})", email, reservationId);
		} else {
			log.warn("[OPT-LOCK] cancel retry attempt={} (email={}, reservationId={})", attempt, email, reservationId);
		}

		return reservationService.cancel(email, reservationId);
	}

	@Recover
	public Reservation recover(OptimisticLockingFailureException e, String email, Long reservationId) {
		log.error(
			"[OPT-LOCK] cancel retry exhausted (attempt={}, email={}, reservationId={}, cause={})",
			getCurrentAttempt(), email, reservationId, e.getClass().getSimpleName(), e
		);
		throw new ReservationException(ErrorCode.RESERVATION_CONCURRENCY_ERROR, "재시도 횟수 초과");
	}

	@Recover
	public Reservation recover(ReservationException e, String email, Long reservationId) {
		throw e;
	}

	private int getCurrentAttempt() {
		RetryContext context = RetrySynchronizationManager.getContext();
		return context == null ? 1 : context.getRetryCount() + 1;
	}
}
