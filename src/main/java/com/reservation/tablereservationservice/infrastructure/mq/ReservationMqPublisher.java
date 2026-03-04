package com.reservation.tablereservationservice.infrastructure.mq;

import static com.reservation.tablereservationservice.global.config.RabbitMqConfig.*;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationMqPublisher {

	private final RabbitTemplate rabbitTemplate;

	public void publish(ReservationRequestMessage message) {
		log.info("[RESERVATION_MQ_PUBLISH] email={}, slotId={}", message.userEmail(), message.slotId());

		// 라우팅 키로 slotId 사용 → 동일 slotId의 예약 요청이 동일 파티션 큐에 직렬 전달
		rabbitTemplate.convertAndSend(RESERVATION_EXCHANGE, String.valueOf(message.slotId()), message);
	}
}
