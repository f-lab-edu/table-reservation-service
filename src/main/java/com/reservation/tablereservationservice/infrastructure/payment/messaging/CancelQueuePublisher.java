package com.reservation.tablereservationservice.infrastructure.payment.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.reservation.tablereservationservice.global.config.CancelQueueProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CancelQueuePublisher {

	private final RabbitTemplate rabbitTemplate;
	private final CancelQueueProperties cancelQueueProperties;

	public void publish(Long reservationId) {
		CancelQueueMessage message = CancelQueueMessage.of(reservationId);
		publish(message);
	}

	public void publish(CancelQueueMessage message) {
		rabbitTemplate.convertAndSend(
				cancelQueueProperties.getExchange(),
				cancelQueueProperties.getRoutingKey(),
				message
		);
		log.info("[CANCEL_QUEUE] 취소 메시지 발행 reservationId={}", message.getReservationId());
	}
}
