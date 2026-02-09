package com.reservation.tablereservationservice.application.reservation.concurrency;

import java.time.LocalDate;

public interface CapacityStrategy {

	void decrease(Long slotId, LocalDate date, int partySize);
}
