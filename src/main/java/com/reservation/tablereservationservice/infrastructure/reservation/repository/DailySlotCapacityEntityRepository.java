package com.reservation.tablereservationservice.infrastructure.reservation.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.reservation.tablereservationservice.infrastructure.reservation.entity.DailySlotCapacityEntity;

import jakarta.persistence.LockModeType;

public interface DailySlotCapacityEntityRepository extends JpaRepository<DailySlotCapacityEntity, Long> {

	Optional<DailySlotCapacityEntity> findBySlotIdAndDate(Long slotSlotId, LocalDate date);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DailySlotCapacityEntity d where d.slotId = :slotId and d.date = :date")
    Optional<DailySlotCapacityEntity> findBySlotIdAndDateForUpdate(
        @Param("slotId") Long slotId,
        @Param("date") LocalDate date
    );
}
