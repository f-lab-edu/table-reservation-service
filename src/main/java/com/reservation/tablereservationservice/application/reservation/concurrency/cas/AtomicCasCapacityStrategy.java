package com.reservation.tablereservationservice.application.reservation.concurrency.cas;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.reservation.tablereservationservice.application.reservation.concurrency.CapacityStrategy;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;

@Component
public class AtomicCasCapacityStrategy implements CapacityStrategy {

	private static final int MAX_CAS_RETRY = 50; // 무한 CPU 스핀을 방지하기 위한 상한값
	private final ConcurrentMap<CapacityKey, AtomicInteger> remainingCache = new ConcurrentHashMap<>();

	public void init(Long slotId, LocalDate date, int initialRemaining) {
		remainingCache.put(new CapacityKey(slotId, date), new AtomicInteger(initialRemaining));
	}

	@Override
	public void decrease(Long slotId, LocalDate date, int partySize) {
		if (partySize <= 0) {
			throw new ReservationException(ErrorCode.INVALID_PARTY_SIZE);
		}

		CapacityKey key = new CapacityKey(slotId, date);
		AtomicInteger remaining = remainingCache.get(key);
		if (remaining == null) {
			throw new IllegalStateException("capacity not initialized: " + key);
		}

		for (int i = 0; i < MAX_CAS_RETRY; i++) {
			int current = remaining.get();

			if (current < partySize) {
				throw new ReservationException(ErrorCode.RESERVATION_CAPACITY_NOT_ENOUGH);
			}

			if (remaining.compareAndSet(current, current - partySize)) {
				return;
			}
		}

		throw new ReservationException(ErrorCode.RESERVATION_NOT_AVAILABLE, "CAS retry exceeded");
	}

	public int getRemaining(Long slotId, LocalDate date) {
		CapacityKey key = new CapacityKey(slotId, date);
		AtomicInteger remaining = remainingCache.get(key);
		if (remaining == null) {
			throw new IllegalStateException("capacity not initialized: " + key);
		}
		return remaining.get();
	}

	public void clear() {
		remainingCache.clear();
	}

	public record CapacityKey(Long slotId, LocalDate date) {
	}
}
