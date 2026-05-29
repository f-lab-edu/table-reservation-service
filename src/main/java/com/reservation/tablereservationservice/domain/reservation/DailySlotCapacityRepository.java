package com.reservation.tablereservationservice.domain.reservation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailySlotCapacityRepository {

	void incrementRemainingCount(Long slotId, LocalDate date, int partySize);

	int decreaseRemainingCount(Long slotId, LocalDate date, int partySize);

	Optional<DailySlotCapacity> findBySlotIdAndDate(Long restaurantSlotId, LocalDate date);

	List<DailySlotCapacity> findAllFromDate(LocalDate date);

	List<DailySlotCapacity> findAllBySlotIdsAndDate(List<Long> slotIds, LocalDate date);

	DailySlotCapacity save(DailySlotCapacity dailySlotCapacity);

	void deleteAll();
}
