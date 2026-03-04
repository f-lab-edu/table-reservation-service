package com.reservation.tablereservationservice.infrastructure.mq;

import static com.reservation.tablereservationservice.global.config.RabbitMqConfig.*;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.reservation.tablereservationservice.application.reservation.service.ReservationService;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.global.util.ProcessingOrderGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationMqConsumer {

	private final ReservationService reservationService;
	private final ProcessingOrderGenerator orderGenerator;

	@RabbitListener(queues = RESERVATION_REQUEST_QUEUE_1, containerFactory = "rabbitListenerContainerFactory")
	public void consumePartition1(ReservationRequestMessage message) {
		doConsume(message);
	}

	@RabbitListener(queues = RESERVATION_REQUEST_QUEUE_2, containerFactory = "rabbitListenerContainerFactory")
	public void consumePartition2(ReservationRequestMessage message) {
		doConsume(message);
	}

	@RabbitListener(queues = RESERVATION_REQUEST_QUEUE_3, containerFactory = "rabbitListenerContainerFactory")
	public void consumePartition3(ReservationRequestMessage message) {
		doConsume(message);
	}

	@RabbitListener(queues = RESERVATION_REQUEST_QUEUE_4, containerFactory = "rabbitListenerContainerFactory")
	public void consumePartition4(ReservationRequestMessage message) {
		doConsume(message);
	}

	private void doConsume(ReservationRequestMessage message) {
		final long processingOrder = orderGenerator.next();
		final String email = message.userEmail();
		final Long slotId = message.slotId();

		log.info("[RESERVATION_MQ] consume seq={}, email={}, slotId={}", processingOrder, email, slotId);

		try {
			reservationService.handleReservationRequest(message, processingOrder);
			log.info("[RESERVATION_MQ] success seq={}, email={}, slotId={}", processingOrder, email, slotId);
		} catch (ReservationException e) {
			log.warn("[RESERVATION_MQ] rejected email={}, slotId={}, reason={}", email, slotId, e.getMessage());
		} catch (Exception e) {
			log.error("[RESERVATION_MQ] error email={}, slotId={}", email, slotId, e);
			throw e; // NACK → DLX → DLQ
		}
	}
}
