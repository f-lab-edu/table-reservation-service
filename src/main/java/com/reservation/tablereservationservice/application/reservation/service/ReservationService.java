package com.reservation.tablereservationservice.application.reservation.service;

import static java.util.stream.Collectors.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacity;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.domain.restaurant.Restaurant;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantRepository;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRepository;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.presentation.common.PageResponseDto;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationListResponseDto;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationSearchDto;

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
	private final RestaurantRepository restaurantRepository;

	@Transactional
	public Reservation create(String email, ReservationRequestDto requestDto) {
		User user = userRepository.fetchByEmail(email);

		RestaurantSlot slot = restaurantSlotRepository.fetchById(requestDto.getSlotId());

		validatePartySize(requestDto.getPartySize(), slot);

		LocalDateTime visitAt = LocalDateTime.of(requestDto.getDate(), slot.getTime());

		// 중복 시간대 예약 검증
		validateDuplicatedTime(user.getUserId(), visitAt);

		DailySlotCapacity capacity = dailySlotCapacityRepository
			.findBySlotIdAndDate(slot.getSlotId(), requestDto.getDate())
			.orElseThrow(() -> new ReservationException(ErrorCode.RESERVATION_SLOT_NOT_OPENED));

		// 수량 검증 및 차감
		decreaseCapacity(capacity, requestDto.getPartySize());

		Reservation reservation = Reservation.builder()
			.userId(user.getUserId())
			.slotId(slot.getSlotId())
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

	@Transactional(readOnly = true)
	public PageResponseDto<ReservationListResponseDto> findMyReservations(
		String email,
		ReservationSearchDto searchDto
	) {
		User user = userRepository.fetchByEmail(email);

		// page reservation 조회
		Page<Reservation> page =
			reservationRepository.findMyReservations(
				user.getUserId(),
				searchDto.getStatus(),
				searchDto.getStartDate().atStartOfDay(),
				searchDto.getEndDate().atTime(LocalTime.MAX),
				searchDto.getPageable()
			);

		if (page.isEmpty()) {
			return PageResponseDto.from(Page.empty(searchDto.getPageable()));
		}

		Map<Long, User> userMap = Map.of(user.getUserId(), user);
		Page<ReservationListResponseDto> dtoPage = createReservationListDtoPage(page, userMap);

		return PageResponseDto.from(dtoPage);
	}

	@Transactional(readOnly = true)
	public PageResponseDto<ReservationListResponseDto> findOwnerReservations(
		String email,
		ReservationSearchDto searchDto
	) {
		User owner = userRepository.fetchByEmail(email);

		List<Long> restaurantIds = restaurantRepository.findAllByOwnerId(owner.getUserId()).stream()
			.map(Restaurant::getRestaurantId)
			.toList();

		if (restaurantIds.isEmpty()) {
			return PageResponseDto.from(Page.empty(searchDto.getPageable()));
		}

		// page reservation 조회
		Page<Reservation> page = reservationRepository.findOwnerReservations(
			restaurantIds,
			searchDto.getStatus(),
			searchDto.getStartDate().atStartOfDay(),
			searchDto.getEndDate().atTime(LocalTime.MAX),
			searchDto.getPageable()
		);

		if (page.isEmpty()) {
			return PageResponseDto.from(Page.empty(searchDto.getPageable()));
		}

		// owner는 예약자(user)가 여러 명이므로 userMap을 따로 구성
		List<Long> userIds = page.getContent().stream()
			.map(Reservation::getUserId)
			.distinct()
			.toList();

		Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
			.collect(toMap(User::getUserId, Function.identity()));

		Page<ReservationListResponseDto> dtoPage = createReservationListDtoPage(page, userMap);

		return PageResponseDto.from(dtoPage);
	}

	private Page<ReservationListResponseDto> createReservationListDtoPage(
		Page<Reservation> page,
		Map<Long, User> userMap
	) {
		Map<Long, RestaurantSlot> slotMap = loadSlotMap(page);
		Map<Long, Restaurant> restaurantMap = loadRestaurantMap(slotMap);

		return page.map(r -> {
			User user = userMap.get(r.getUserId());
			if (user == null) {
				throw new ReservationException(ErrorCode.RESOURCE_NOT_FOUND,
					"User (userId=" + r.getUserId() + ")");
			}

			RestaurantSlot slot = slotMap.get(r.getSlotId());
			if (slot == null) {
				throw new ReservationException(ErrorCode.RESOURCE_NOT_FOUND,
					"RestaurantSlot (slotId=" + r.getSlotId() + ")"
				);
			}

			Restaurant restaurant = restaurantMap.get(slot.getRestaurantId());
			if (restaurant == null) {
				throw new ReservationException(ErrorCode.RESOURCE_NOT_FOUND,
					"Restaurant (restaurantId=" + slot.getRestaurantId() + ")");
			}

			return ReservationListResponseDto.of(user, r, restaurant);
		});
	}

	private Map<Long, RestaurantSlot> loadSlotMap(Page<Reservation> page) {
		List<Long> slotIds = page.getContent().stream()
			.map(Reservation::getSlotId)
			.distinct()
			.toList();

		return restaurantSlotRepository.findAllById(slotIds).stream()
			.collect(toMap(RestaurantSlot::getSlotId, Function.identity()));
	}

	private Map<Long, Restaurant> loadRestaurantMap(Map<Long, RestaurantSlot> slotMap) {
		List<Long> restaurantIds = slotMap.values().stream()
			.map(RestaurantSlot::getRestaurantId)
			.distinct()
			.toList();

		return restaurantRepository.findAllById(restaurantIds).stream()
			.collect(toMap(Restaurant::getRestaurantId, Function.identity()));
	}

	private void validateDuplicatedTime(Long userId, LocalDateTime visitAt) {
		if (reservationRepository.existsByUserIdAndVisitAtAndStatus(userId, visitAt, ReservationStatus.CONFIRMED)) {
			throw new ReservationException(ErrorCode.RESERVATION_DUPLICATED_TIME);
		}
	}

	private void validatePartySize(int partySize, RestaurantSlot slot) {
		if (!slot.canAcceptPartySize(partySize)) {
			throw new ReservationException(ErrorCode.INVALID_PARTY_SIZE);
		}
	}

	private void decreaseCapacity(DailySlotCapacity capacity, int partySize) {
		if (!capacity.decrease(partySize)) {
			throw new ReservationException(ErrorCode.RESERVATION_CAPACITY_NOT_ENOUGH);
		}
		dailySlotCapacityRepository.updateRemainingCount(capacity);
	}

}
