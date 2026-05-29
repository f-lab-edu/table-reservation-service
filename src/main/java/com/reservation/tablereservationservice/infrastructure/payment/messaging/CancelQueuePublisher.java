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
		rabbitTemplate.convertAndSend(
				cancelQueueProperties.getExchange(),
				cancelQueueProperties.getRoutingKey(),
				new CancelQueueMessage(reservationId)
		);
		log.info("[CANCEL_QUEUE] 취소 메시지 발행 reservationId={}", reservationId);
	}
}
