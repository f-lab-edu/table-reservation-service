package com.reservation.tablereservationservice.domain.reservation;

import java.time.LocalDate;
import java.util.Optional;

public interface DailySlotCapacityRepository {

	Optional<DailySlotCapacity> findBySlotIdAndDate(Long restaurantSlotId, LocalDate date);

	DailySlotCapacity save(DailySlotCapacity dailySlotCapacity);

	void update(DailySlotCapacity dailySlotCapacity);

	void deleteAll();
}
