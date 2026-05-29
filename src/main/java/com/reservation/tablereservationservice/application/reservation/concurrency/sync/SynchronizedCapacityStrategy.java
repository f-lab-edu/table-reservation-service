package com.reservation.tablereservationservice.application.reservation.concurrency.sync;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.reservation.tablereservationservice.application.reservation.concurrency.CapacityStrategy;
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
			int affected = dailySlotCapacityRepository.decreaseRemainingCount(slotId, date, partySize);
			if (affected == 0) {
				throw new ReservationException(ErrorCode.RESERVATION_CAPACITY_NOT_ENOUGH);
			}
		}
	}
}
