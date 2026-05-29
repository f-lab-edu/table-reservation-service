package com.reservation.tablereservationservice.application.reservation.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.global.config.CancelQueueProperties;
import com.reservation.tablereservationservice.infrastructure.payment.messaging.CancelQueuePublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CancelPendingRecoveryScheduler {

	private final ReservationRepository reservationRepository;
	private final CancelQueuePublisher cancelQueuePublisher;
	private final CancelQueueProperties cancelQueueProperties;

	@Scheduled(fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
	public void recoverCancelPending() {
		LocalDateTime threshold = LocalDateTime.now().minusMinutes(cancelQueueProperties.getRecoveryThresholdMinutes());
		List<Reservation> stale = reservationRepository.findCancelPendingBefore(threshold);

		stale.forEach(reservation -> {
			try {
				cancelQueuePublisher.publish(reservation.getReservationId());
			} catch (Exception e) {
				log.warn("[CANCEL_RECOVERY] MQ 재발행 실패 reservationId={}", reservation.getReservationId(), e);
			}
		});

		if (!stale.isEmpty()) {
			log.info("[CANCEL_RECOVERY] CANCEL_PENDING 재발행 count={}", stale.size());
		}
	}
}
