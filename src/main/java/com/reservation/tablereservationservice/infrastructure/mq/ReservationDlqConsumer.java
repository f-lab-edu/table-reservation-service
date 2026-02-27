package com.reservation.tablereservationservice.infrastructure.mq;

import static com.reservation.tablereservationservice.global.config.RabbitMqConfig.*;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ReservationDlqConsumer {

	@RabbitListener(queues = RESERVATION_DLQ, containerFactory = "rabbitListenerContainerFactory")
	public void consume(ReservationRequestMessage message) {
		log.error("[DLQ] 기술적 장애로 처리 실패 email={}, slotId={}", message.userEmail(), message.slotId());
		// TODO: 재시도 정책 미결정으로 미구현
	}
}
