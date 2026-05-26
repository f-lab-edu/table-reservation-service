package com.reservation.tablereservationservice.application.reservation.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.global.config.ReservationStreamProperties;
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

	@Scheduled(fixedDelayString = "${redis.reservation.pending-key-ttl-seconds:900}000")
	@Transactional
	public void expirePendingReservations() {
		int ttlSeconds = streamProperties.getPendingKeyTtlSeconds();
		LocalDateTime threshold = LocalDateTime.now().minusSeconds(ttlSeconds);

		List<Reservation> expired = reservationRepository.findPendingBefore(threshold);
		if (expired.isEmpty()) {
			return;
		}

		for (Reservation reservation : expired) {
			reservation.fail();
			reservationRepository.updateStatus(reservation);

			dailySlotCapacityRepository
					.findBySlotIdAndDate(reservation.getSlotId(), reservation.getVisitAt().toLocalDate())
					.ifPresent(capacity -> {
						capacity.increase(reservation.getPartySize());
						dailySlotCapacityRepository.updateRemainingCount(capacity);
					});

			reservationPublisher.releaseSeat(
					reservation.getSlotId(),
					reservation.getVisitAt().toLocalDate(),
					reservation.getPartySize()
			);
		}

		log.info("[EXPIRE_SCHEDULER] PENDING 예약 만료 처리 count={}", expired.size());
	}
}
