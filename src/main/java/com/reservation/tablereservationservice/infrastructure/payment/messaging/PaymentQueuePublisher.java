package com.reservation.tablereservationservice.infrastructure.payment.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.reservation.tablereservationservice.global.config.PaymentQueueProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentQueuePublisher {

	private final RabbitTemplate rabbitTemplate;
	private final PaymentQueueProperties paymentQueueProperties;

	public void publish(PaymentQueueMessage message) {
		rabbitTemplate.convertAndSend(
				paymentQueueProperties.getExchange(),
				paymentQueueProperties.getRoutingKey(),
				message
		);
		log.info("[PAYMENT_QUEUE] 결제 확정 메시지 발행 reservationId={}", message.getReservationId());
	}
}
