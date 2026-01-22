package com.reservation.tablereservationservice.infrastructure.reservation.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reservation.tablereservationservice.infrastructure.reservation.entity.DailySlotCapacityEntity;

public interface DailySlotCapacityEntityRepository extends JpaRepository<DailySlotCapacityEntity, Long> {

	Optional<DailySlotCapacityEntity> findBySlot_SlotIdAndDate(Long slotId, LocalDate date);

}
