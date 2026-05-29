package com.reservation.tablereservationservice.infrastructure.reservation.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.reservation.tablereservationservice.infrastructure.reservation.entity.DailySlotCapacityEntity;

public interface DailySlotCapacityEntityRepository extends JpaRepository<DailySlotCapacityEntity, Long> {

	Optional<DailySlotCapacityEntity> findBySlotIdAndDate(Long slotSlotId, LocalDate date);

	List<DailySlotCapacityEntity> findAllByDateGreaterThanEqual(LocalDate date);

	List<DailySlotCapacityEntity> findAllBySlotIdInAndDate(List<Long> slotIds, LocalDate date);

	@Modifying
	@Query("UPDATE DailySlotCapacityEntity d SET d.remainingCount = d.remainingCount + :partySize WHERE d.slotId = :slotId AND d.date = :date")
	void incrementRemainingCount(@Param("slotId") Long slotId, @Param("date") LocalDate date, @Param("partySize") int partySize);

	@Modifying
	@Query("UPDATE DailySlotCapacityEntity d SET d.remainingCount = d.remainingCount - :partySize WHERE d.slotId = :slotId AND d.date = :date AND d.remainingCount >= :partySize")
	int decreaseRemainingCount(@Param("slotId") Long slotId, @Param("date") LocalDate date, @Param("partySize") int partySize);
}
