package com.reservation.tablereservationservice.application.reservation.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacity;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRepository;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.global.exception.UserException;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

	private final UserRepository userRepository;
	private final RestaurantSlotRepository restaurantSlotRepository;
	private final DailySlotCapacityRepository dailySlotCapacityRepository;
	private final ReservationRepository reservationRepository;

	@Transactional
	public Reservation create(String email, ReservationRequestDto requestDto) {
		User user = userRepository.findByEmail(email)
			.orElseThrow(() -> new UserException(ErrorCode.RESOURCE_NOT_FOUND, "User"));

		RestaurantSlot slot = restaurantSlotRepository.findById(requestDto.getSlotId())
			.orElseThrow(() -> new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "RestaurantSlot"));

		LocalDateTime visitAt = calculateVisitAt(requestDto.getDate(), slot);

		// 중복 시간대 예약 검증
		validateDuplicatedTime(user.getUserId(), visitAt);

		DailySlotCapacity capacity = dailySlotCapacityRepository
			.findBySlotIdAndDate(slot.getSlotId(), requestDto.getDate())
			.orElseThrow(() -> new ReservationException(ErrorCode.RESERVATION_SLOT_NOT_OPENED));

		// 수량 검증 및 차감
		validateEnoughCapacity(capacity, requestDto.getPartySize());
		decreaseCapacity(capacity, requestDto.getPartySize());

		Reservation reservation = Reservation.builder()
			.userId(user.getUserId())
			.slotId(slot.getSlotId())
			.date(requestDto.getDate())
			.visitAt(visitAt)
			.partySize(requestDto.getPartySize())
			.note(requestDto.getNote())
			.status(ReservationStatus.CONFIRMED)
			.build();

		try {
			return reservationRepository.save(reservation);
		} catch (DataIntegrityViolationException e) {
			throw new ReservationException(ErrorCode.RESERVATION_DUPLICATED_TIME);
		}

	}

	private LocalDateTime calculateVisitAt(LocalDate date, RestaurantSlot slot) {
		return LocalDateTime.of(date, slot.getTime());
	}

	private void validateDuplicatedTime(Long userId, LocalDateTime visitAt) {
		if (reservationRepository.existsByUserIdAndVisitAtAndStatus(userId, visitAt, ReservationStatus.CONFIRMED)) {
			throw new ReservationException(ErrorCode.RESERVATION_DUPLICATED_TIME);
		}
	}

	private void validateEnoughCapacity(DailySlotCapacity capacity, int partySize) {
		if (!capacity.hasEnough(partySize)) {
			throw new ReservationException(ErrorCode.RESERVATION_CAPACITY_NOT_ENOUGH);
		}
	}

	private void decreaseCapacity(DailySlotCapacity capacity, int partySize) {
		capacity.decrease(partySize);
		dailySlotCapacityRepository.update(capacity);

	}

}
