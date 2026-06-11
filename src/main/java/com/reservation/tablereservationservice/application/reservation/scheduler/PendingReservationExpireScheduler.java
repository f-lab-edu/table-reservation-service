package com.reservation.tablereservationservice.application.reservation.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.reservation.tablereservationservice.application.payment.PaymentClient;
import com.reservation.tablereservationservice.application.payment.PaymentResult;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.global.config.ReservationStreamProperties;
import com.reservation.tablereservationservice.global.exception.PaymentException;
import com.reservation.tablereservationservice.infrastructure.payment.messaging.PaymentQueueMessage;
import com.reservation.tablereservationservice.infrastructure.payment.messaging.PaymentQueuePublisher;
import com.reservation.tablereservationservice.infrastructure.redis.ReservationPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingReservationExpireScheduler {

	private final ReservationRepository reservationRepository;
	private final DailySlotCapacityRepository dailySlotCapacityRepository;
	private final ReservationPublisher reservationPublisher;
	private final ReservationStreamProperties streamProperties;
	private final PaymentClient paymentClient;
	private final PaymentQueuePublisher paymentQueuePublisher;

	public void expirePendingReservations() {
		LocalDateTime threshold = LocalDateTime.now().minusSeconds(streamProperties.getPendingExpireThresholdSeconds());
		List<Reservation> expired = reservationRepository.findPendingBefore(threshold);

		if (expired.isEmpty()) {
			return;
		}

		expired.forEach(this::processExpiredReservation);
		log.info("[EXPIRE_SCHEDULER] 만료 처리 완료 count={}", expired.size());
	}

	private void processExpiredReservation(Reservation reservation) {
		if (reservation.getPaymentKey() == null) {
			log.info("[EXPIRE_SCHEDULER] 결제 시도 없이 만료 reservationId={}", reservation.getReservationId());
			failReservation(reservation);
			return;
		}

		if (!recoverIfApproved(reservation)) {
			failReservation(reservation);
		}
	}

	private boolean recoverIfApproved(Reservation reservation) {
		try {
			PaymentResult result = paymentClient.queryByPaymentKey(reservation.getPaymentKey());
			if (!result.isDone()) {
				return false;
			}
			PaymentQueueMessage message = PaymentQueueMessage.ofConfirmed(reservation, reservation.getIdempotencyKey(), result);
			paymentQueuePublisher.publish(message);
			log.info("[EXPIRE_SCHEDULER] 결제 확인으로 예약 복구 reservationId={}", reservation.getReservationId());
			return true;
		} catch (PaymentException e) {
			log.warn("[EXPIRE_SCHEDULER] Toss 조회 실패 — 만료 처리 reservationId={}", reservation.getReservationId());
			return false;
		}
	}

	private void failReservation(Reservation reservation) {
		int affected = reservationRepository.updateStatusConditional(
				reservation.getReservationId(),
				ReservationStatus.PENDING,
				ReservationStatus.PAYMENT_FAILED);
		if (affected == 0) {
			log.info("[EXPIRE_SCHEDULER] 이미 처리된 예약 — 건너뜀 reservationId={}", reservation.getReservationId());
			return;
		}

		dailySlotCapacityRepository.incrementRemainingCount(
				reservation.getSlotId(),
				reservation.getVisitAt().toLocalDate(),
				reservation.getPartySize()
		);

		reservationPublisher.releaseSeat(
				reservation.getSlotId(),
				reservation.getVisitAt().toLocalDate(),
				reservation.getPartySize()
		);

		log.info("[EXPIRE_SCHEDULER] 예약 만료 처리 reservationId={}", reservation.getReservationId());
	}
}
