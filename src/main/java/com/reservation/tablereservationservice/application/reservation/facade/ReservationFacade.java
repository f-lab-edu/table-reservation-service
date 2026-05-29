package com.reservation.tablereservationservice.application.reservation.facade;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.reservation.tablereservationservice.application.reservation.service.ReservationCreateResult;
import com.reservation.tablereservationservice.application.reservation.service.ReservationService;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRepository;
import com.reservation.tablereservationservice.global.annotation.Idempotent;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.infrastructure.payment.messaging.CancelQueuePublisher;
import com.reservation.tablereservationservice.infrastructure.redis.ReservationPublisher;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationFacade {

	private final ReservationService reservationService;
	private final ReservationPublisher reservationPublisher;
	private final ReservationRepository reservationRepository;
	private final UserRepository userRepository;
	private final CancelQueuePublisher cancelQueuePublisher;

	@Idempotent
	public ReservationCreateResult reserve(String email, ReservationRequestDto requestDto, String idempotencyKey) {
		reservationPublisher.holdSeat(requestDto.getSlotId(), requestDto.getDate(), requestDto.getPartySize());
		try {
			return reservationService.reserve(email, requestDto, idempotencyKey);
		} catch (Exception e) {
			reservationPublisher.releaseSeat(requestDto.getSlotId(), requestDto.getDate(), requestDto.getPartySize());
			throw e; // 원래 예외 재던짐 — 클라이언트에 그대로 전달
		}
	}

	public void cancel(String email, Long reservationId) {
		Reservation reservation = reservationRepository.fetchById(reservationId);
		User user = userRepository.fetchByEmail(email);

		if (!reservation.isOwner(user.getUserId())) {
			throw new ReservationException(ErrorCode.RESERVATION_FORBIDDEN);
		}

		ReservationStatus status = reservation.getStatus();

		if (status == ReservationStatus.CANCEL_PENDING) {
			// MQ 발행 실패로 CANCEL_PENDING 체류 중인 경우 재발행하여 복구
			// Outbox 패턴 도입 시 발행 실패 리스크가 사라지므로 제거 가능
			log.info("[CANCEL] CANCEL_PENDING — MQ 재발행 reservationId={}", reservationId);
			cancelQueuePublisher.publish(reservationId);
			return;
		}

		validateCancelable(status, reservation);

		reservationService.markCancelPending(reservationId);
		cancelQueuePublisher.publish(reservationId);
	}

	private void validateCancelable(ReservationStatus status, Reservation reservation) {
		if (status == ReservationStatus.CANCELED) {
			throw new ReservationException(ErrorCode.RESERVATION_ALREADY_CANCELED);
		}
		if (status != ReservationStatus.CONFIRMED) {
			throw new ReservationException(ErrorCode.RESERVATION_NOT_CANCELABLE);
		}
		if (!reservation.canCancelAt(LocalDateTime.now())) {
			throw new ReservationException(ErrorCode.RESERVATION_CANCEL_DEADLINE_PASSED);
		}
	}
}
