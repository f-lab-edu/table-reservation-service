package com.reservation.tablereservationservice.infrastructure.payment.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.reservation.tablereservationservice.application.payment.service.PaymentConfirmationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentQueueConsumer {

	private final PaymentConfirmationService paymentConfirmationService;

	@RabbitListener(queues = "${rabbitmq.payment.queue}", containerFactory = "paymentContainerFactory")
	public void handlePaymentConfirm(PaymentQueueMessage message) {
		try {
			paymentConfirmationService.confirm(message);
			log.info("[PAYMENT_CONSUMER] 결제 확정 완료 reservationId={}", message.getReservationId());
		} catch (DataIntegrityViolationException e) {
			// idempotency_key UNIQUE 제약 위반 — 동일 메시지 중복 수신. ack 처리
			log.warn("[PAYMENT_CONSUMER] 중복 결제 메시지 감지 — ack 처리. idempotencyKey={}", message.getIdempotencyKey());
		} catch (Exception e) {
			log.error("[PAYMENT_CONSUMER] 결제 확정 실패 reservationId={}", message.getReservationId(), e);
			throw e;
		}
	}
}
