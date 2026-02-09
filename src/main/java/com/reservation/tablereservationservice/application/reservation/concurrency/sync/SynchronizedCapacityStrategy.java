package com.reservation.tablereservationservice.application.reservation.concurrency.sync;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.reservation.tablereservationservice.application.reservation.concurrency.CapacityStrategy;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacity;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SynchronizedCapacityStrategy implements CapacityStrategy {

	private final DailySlotCapacityRepository dailySlotCapacityRepository;
	private final Object monitor = new Object();

	@Override
	public void decrease(Long slotId, LocalDate date, int partySize) {
		synchronized (monitor) {
			DailySlotCapacity capacity = dailySlotCapacityRepository.findBySlotIdAndDate(slotId, date)
				.orElseThrow(() -> new ReservationException(ErrorCode.RESERVATION_SLOT_NOT_OPENED));

			if (!capacity.decrease(partySize)) {
				throw new ReservationException(ErrorCode.RESERVATION_CAPACITY_NOT_ENOUGH);
			}

			dailySlotCapacityRepository.updateRemainingCount(capacity);
		}
	}
}
