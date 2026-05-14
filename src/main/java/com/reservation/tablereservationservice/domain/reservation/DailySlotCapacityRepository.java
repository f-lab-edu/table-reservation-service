package com.reservation.tablereservationservice.domain.reservation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailySlotCapacityRepository {

	Optional<DailySlotCapacity> findBySlotIdAndDate(Long restaurantSlotId, LocalDate date);

	List<DailySlotCapacity> findAllFromDate(LocalDate date);

	Optional<DailySlotCapacity> findBySlotIdAndDateForUpdate(Long restaurantSlotId, LocalDate date);

	DailySlotCapacity save(DailySlotCapacity dailySlotCapacity);

	void updateRemainingCount(DailySlotCapacity capacity);

	void deleteAll();
}
