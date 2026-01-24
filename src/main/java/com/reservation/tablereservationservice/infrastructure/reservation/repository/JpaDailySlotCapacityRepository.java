package com.reservation.tablereservationservice.infrastructure.reservation.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacity;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.infrastructure.reservation.entity.DailySlotCapacityEntity;
import com.reservation.tablereservationservice.infrastructure.reservation.mapper.ReservationMapper;
import com.reservation.tablereservationservice.infrastructure.restaurant.entity.RestaurantSlotEntity;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JpaDailySlotCapacityRepository implements DailySlotCapacityRepository {

	private final DailySlotCapacityEntityRepository dailySlotCapacityEntityRepository;
	private final EntityManager em;

	@Override
	public Optional<DailySlotCapacity> findBySlotIdAndDate(Long slotId, LocalDate date) {
		return dailySlotCapacityEntityRepository
			.findBySlot_SlotIdAndDate(slotId, date)
			.map(ReservationMapper.INSTANCE::toDomain);
	}

	@Override
	public DailySlotCapacity save(DailySlotCapacity dailySlotCapacity) {
		DailySlotCapacityEntity entity = ReservationMapper.INSTANCE.toEntity(dailySlotCapacity);

		entity.assignSlot(em.getReference(RestaurantSlotEntity.class, dailySlotCapacity.getSlotId()));

		DailySlotCapacityEntity saved = dailySlotCapacityEntityRepository.save(entity);
		return ReservationMapper.INSTANCE.toDomain(saved);
	}

	@Override
	public void update(DailySlotCapacity dailySlotCapacity) {
		DailySlotCapacityEntity entity = dailySlotCapacityEntityRepository.findById(dailySlotCapacity.getCapacityId())
			.orElseThrow(() -> new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "DailySlotCapacity"));

		entity.updateRemainingCount(dailySlotCapacity.getRemainingCount());

		dailySlotCapacityEntityRepository.save(entity);
	}

	@Override
	public void deleteAll() {
		dailySlotCapacityEntityRepository.deleteAll();
	}

}
