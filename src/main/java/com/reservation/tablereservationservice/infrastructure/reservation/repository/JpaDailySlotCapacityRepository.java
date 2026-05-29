package com.reservation.tablereservationservice.infrastructure.reservation.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacity;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.infrastructure.reservation.entity.DailySlotCapacityEntity;
import com.reservation.tablereservationservice.infrastructure.reservation.mapper.ReservationMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JpaDailySlotCapacityRepository implements DailySlotCapacityRepository {

	private final DailySlotCapacityEntityRepository dailySlotCapacityEntityRepository;

	@Override
	public Optional<DailySlotCapacity> findBySlotIdAndDate(Long slotId, LocalDate date) {
		return dailySlotCapacityEntityRepository
				.findBySlotIdAndDate(slotId, date)
				.map(ReservationMapper.INSTANCE::toDomain);
	}

	@Override
	public DailySlotCapacity save(DailySlotCapacity dailySlotCapacity) {
		DailySlotCapacityEntity entity = ReservationMapper.INSTANCE.toEntity(dailySlotCapacity);

		DailySlotCapacityEntity saved = dailySlotCapacityEntityRepository.save(entity);
		return ReservationMapper.INSTANCE.toDomain(saved);
	}

	@Override
	@Transactional
	public int decreaseRemainingCount(Long slotId, LocalDate date, int partySize) {
		return dailySlotCapacityEntityRepository.decreaseRemainingCount(slotId, date, partySize);
	}

	@Override
	public List<DailySlotCapacity> findAllFromDate(LocalDate date) {
		return dailySlotCapacityEntityRepository.findAllByDateGreaterThanEqual(date)
				.stream()
				.map(ReservationMapper.INSTANCE::toDomain)
				.toList();
	}

	@Override
	@Transactional
	public void incrementRemainingCount(Long slotId, LocalDate date, int partySize) {
		dailySlotCapacityEntityRepository.incrementRemainingCount(slotId, date, partySize);
	}

	@Override
	public void deleteAll() {
		dailySlotCapacityEntityRepository.deleteAll();
	}

}
