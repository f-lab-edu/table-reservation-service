package com.reservation.tablereservationservice.application.reservation.service;

import static java.util.stream.Collectors.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reservation.tablereservationservice.application.notification.NotificationService;
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
import com.reservation.tablereservationservice.global.transaction.TransactionHandler;
import com.reservation.tablereservationservice.infrastructure.redis.ReservationPublisher;
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
	private final ReservationPublisher reservationPublisher;
	private final NotificationService notificationService;
	private final TransactionHandler transactionHandler;

	@Transactional
	public ReservationCreateResult reserve(String email, ReservationRequestDto requestDto, String idempotencyKey) {
		User user = userRepository.fetchByEmail(email);
		RestaurantSlot slot = restaurantSlotRepository.fetchById(requestDto.getSlotId());
		LocalDateTime visitAt = LocalDateTime.of(requestDto.getDate(), slot.getTime());

		validatePartySize(requestDto.getPartySize(), slot);
		validateDuplicatedTime(user.getUserId(), visitAt);

		reservationPublisher.holdSeat(slot.getSlotId(), requestDto.getDate(), requestDto.getPartySize());
		transactionHandler.runOnRollback(() ->
				reservationPublisher.releaseSeat(slot.getSlotId(), requestDto.getDate(), requestDto.getPartySize()));

		DailySlotCapacity capacity = dailySlotCapacityRepository
				.findBySlotIdAndDate(slot.getSlotId(), requestDto.getDate())
				.orElseThrow(() -> new ReservationException(ErrorCode.RESERVATION_SLOT_NOT_OPENED));
		decreaseCapacity(capacity, requestDto.getPartySize());

		Reservation reservation = Reservation.builder()
				.userId(user.getUserId())
				.slotId(slot.getSlotId())
				.visitAt(visitAt)
				.partySize(requestDto.getPartySize())
				.note(requestDto.getNote())
				.idempotencyKey(idempotencyKey)
				.status(ReservationStatus.PENDING)
				.build();

		reservationRepository.save(reservation);
		return new ReservationCreateResult(reservation, slot.depositAmount(requestDto.getPartySize()));
	}

	@Transactional(readOnly = true)
	public PageResponseDto<ReservationListResponseDto> findMyReservations(String email, ReservationSearchDto searchDto) {
		User user = userRepository.fetchByEmail(email);

		Page<Reservation> page = reservationRepository.findMyReservations(
				user.getUserId(),
				searchDto.getStatus(),
				searchDto.getStartDate().atStartOfDay(),
				searchDto.getEndDate().atTime(LocalTime.MAX),
				searchDto.getPageable()
		);

		if (page.isEmpty()) {
			return PageResponseDto.from(Page.empty(searchDto.getPageable()));
		}

		Map<Long, User> idToUser = Map.of(user.getUserId(), user);
		return PageResponseDto.from(createReservationListDtoPage(page, idToUser));
	}

	@Transactional(readOnly = true)
	public PageResponseDto<ReservationListResponseDto> findOwnerReservations(String email, ReservationSearchDto searchDto) {
		User owner = userRepository.fetchByEmail(email);

		List<Long> restaurantIds = restaurantRepository.findAllByOwnerId(owner.getUserId()).stream()
				.map(Restaurant::getRestaurantId)
				.toList();

		if (restaurantIds.isEmpty()) {
			return PageResponseDto.from(Page.empty(searchDto.getPageable()));
		}

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

		List<Long> userIds = page.getContent().stream().map(Reservation::getUserId).distinct().toList();
		Map<Long, User> idToUser = userRepository.findAllById(userIds).stream()
				.collect(toMap(User::getUserId, Function.identity()));

		return PageResponseDto.from(createReservationListDtoPage(page, idToUser));
	}

	@Transactional
	public Reservation cancel(String email, Long reservationId) {
		User user = userRepository.fetchByEmail(email);
		Reservation reservation = reservationRepository.fetchById(reservationId);

		validateCancelable(user.getUserId(), reservation, LocalDateTime.now());
		reservation.cancel();

		DailySlotCapacity capacity = dailySlotCapacityRepository.findBySlotIdAndDate(reservation.getSlotId(), reservation.getVisitAt().toLocalDate())
				.orElseThrow(() -> new ReservationException(ErrorCode.RESERVATION_SLOT_NOT_OPENED));

		restoreCapacity(capacity, reservation.getPartySize());
		reservationRepository.updateStatus(reservation);

		Restaurant restaurant = findRestaurantBySlotId(reservation.getSlotId());
		transactionHandler.runAfterCommit(() ->
				notificationService.notifyCanceled(reservation, restaurant.getOwnerId(), restaurant.getName()));

		return reservation;
	}

	private void validateDuplicatedTime(Long userId, LocalDateTime visitAt) {
		if (reservationRepository.existsByUserIdAndVisitAtAndStatusIn(
				userId, visitAt, List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED))) {
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

	private void validateCancelable(Long userId, Reservation reservation, LocalDateTime now) {
		if (!reservation.isOwner(userId)) {
			throw new ReservationException(ErrorCode.RESERVATION_FORBIDDEN);
		}
		if (reservation.isAlreadyCanceled()) {
			throw new ReservationException(ErrorCode.RESERVATION_ALREADY_CANCELED);
		}
		if (!reservation.canCancelAt(now)) {
			throw new ReservationException(ErrorCode.RESERVATION_CANCEL_DEADLINE_PASSED);
		}
	}

	private void restoreCapacity(DailySlotCapacity capacity, int partySize) {
		capacity.increase(partySize);
		dailySlotCapacityRepository.updateRemainingCount(capacity);
	}

	private Page<ReservationListResponseDto> createReservationListDtoPage(
			Page<Reservation> page,
			Map<Long, User> idToUser
	) {
		Map<Long, RestaurantSlot> idToSlot = loadSlotMap(page);
		Map<Long, Restaurant> idToRestaurant = loadRestaurantMap(idToSlot);

		return page.map(reservation -> {
			User user = idToUser.get(reservation.getUserId());
			if (user == null) {
				throw new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "User (userId=" + reservation.getUserId() + ")");
			}
			RestaurantSlot slot = idToSlot.get(reservation.getSlotId());
			if (slot == null) {
				throw new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "RestaurantSlot (slotId=" + reservation.getSlotId() + ")");
			}
			Restaurant restaurant = idToRestaurant.get(slot.getRestaurantId());
			if (restaurant == null) {
				throw new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "Restaurant (restaurantId=" + slot.getRestaurantId() + ")");
			}
			return ReservationListResponseDto.of(user, reservation, restaurant);
		});
	}

	private Map<Long, RestaurantSlot> loadSlotMap(Page<Reservation> page) {
		List<Long> slotIds = page.getContent().stream().map(Reservation::getSlotId).distinct().toList();
		return restaurantSlotRepository.findAllById(slotIds).stream()
				.collect(toMap(RestaurantSlot::getSlotId, Function.identity()));
	}

	private Map<Long, Restaurant> loadRestaurantMap(Map<Long, RestaurantSlot> idToSlot) {
		List<Long> restaurantIds = idToSlot.values().stream().map(RestaurantSlot::getRestaurantId).distinct().toList();
		return restaurantRepository.findAllById(restaurantIds).stream()
				.collect(toMap(Restaurant::getRestaurantId, Function.identity()));
	}

	private Restaurant findRestaurantBySlotId(Long slotId) {
		RestaurantSlot slot = restaurantSlotRepository.fetchById(slotId);
		return restaurantRepository.findById(slot.getRestaurantId())
				.orElseThrow(() -> new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "Restaurant"));
	}
}
