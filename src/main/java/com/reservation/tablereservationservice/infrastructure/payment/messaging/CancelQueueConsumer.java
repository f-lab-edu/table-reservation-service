package com.reservation.tablereservationservice.infrastructure.payment.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.reservation.tablereservationservice.application.payment.PaymentClient;
import com.reservation.tablereservationservice.application.reservation.service.ReservationService;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CancelQueueConsumer {

	private final ReservationRepository reservationRepository;
	private final PaymentClient paymentClient;
	private final ReservationService reservationService;

	@RabbitListener(queues = "${rabbitmq.cancel.queue}", containerFactory = "cancelContainerFactory")
	public void handleCancelConfirm(CancelQueueMessage message) {
		Long reservationId = message.getReservationId();
		Reservation reservation = reservationRepository.fetchById(reservationId);

		if (reservation.getStatus() == ReservationStatus.CANCELED) {
			log.warn("[CANCEL_CONSUMER] 중복 취소 메시지 — ack reservationId={}", reservationId);
			return;
		}
		if (reservation.getStatus() != ReservationStatus.CANCEL_PENDING) {
			log.warn("[CANCEL_CONSUMER] 비정상 상태 — ack reservationId={} status={}", reservationId, reservation.getStatus());
			return;
		}

		try {
			if (reservation.getPaymentKey() != null) {
				paymentClient.cancel(reservation.getPaymentKey(), reservationId); // 트랜잭션 밖, CB/Retry 적용
			}
			reservationService.confirmCancel(reservation);
			log.info("[CANCEL_CONSUMER] 취소 확정 완료 reservationId={}", reservationId);
		} catch (Exception e) {
			log.error("[CANCEL_CONSUMER] 취소 확정 실패 — nack reservationId={}", reservationId, e);
			throw e; // nack → retry-queue → DLQ
		}
	}
}
