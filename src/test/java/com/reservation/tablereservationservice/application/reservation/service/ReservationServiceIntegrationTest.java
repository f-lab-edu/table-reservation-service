package com.reservation.tablereservationservice.application.reservation.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacity;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.domain.restaurant.CategoryCode;
import com.reservation.tablereservationservice.domain.restaurant.RegionCode;
import com.reservation.tablereservationservice.domain.restaurant.Restaurant;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantRepository;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRepository;
import com.reservation.tablereservationservice.domain.user.UserRole;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReservationServiceIntegrationTest {

	@Autowired
	private ReservationService reservationService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RestaurantRepository restaurantRepository;

	@Autowired
	private RestaurantSlotRepository restaurantSlotRepository;

	@Autowired
	private DailySlotCapacityRepository dailySlotCapacityRepository;

	@Autowired
	private ReservationRepository reservationRepository;

	private String email;
	private Long userId;
	private Long slotId;
	private LocalDate date;
	private LocalTime slotTime;

	@BeforeEach
	void setUp() {
		this.email = "customer01@test.com";
		this.date = LocalDate.now();
		this.slotTime = LocalTime.of(19, 0);

		User customer = User.builder()
			.email(email)
			.name("customer01")
			.phone("010-0000-0000")
			.password("encrypted-password")
			.userRole(UserRole.CUSTOMER)
			.build();
		User savedCustomer = userRepository.save(customer);

		User owner = User.builder()
			.email("owner@test.com")
			.name("owner")
			.phone("010-0000-0001")
			.password("encrypted-password")
			.userRole(UserRole.OWNER)
			.build();
		User savedOwner = userRepository.save(owner);

		Restaurant restaurant = Restaurant.builder()
			.name("강남 한상")
			.regionCode(RegionCode.RG01)
			.categoryCode(CategoryCode.CT01)
			.address("서울 강남구 테헤란로 1")
			.ownerId(savedOwner.getUserId())
			.build();
		Restaurant savedRestaurant = restaurantRepository.save(restaurant);

		RestaurantSlot restaurantSlot = RestaurantSlot.builder()
			.restaurantId(savedRestaurant.getRestaurantId())
			.time(slotTime)
			.maxCapacity(10)
			.build();
		RestaurantSlot savedRestaurantSlot = restaurantSlotRepository.save(restaurantSlot);

		this.userId = savedCustomer.getUserId();
		this.slotId = savedRestaurantSlot.getSlotId();
	}

	@Test
	@DisplayName("예약 생성 성공 - CONFIRMED 저장 + capacity 차감")
	void create_success_confirmed_and_decreaseCapacity() {
		// given
		int partySize = 2;
		saveCapacity(slotId, date, 10);

		ReservationRequestDto req = new ReservationRequestDto(slotId, date, partySize, "note");
		LocalDateTime visitAt = LocalDateTime.of(date, slotTime);

		DailySlotCapacity before = dailySlotCapacityRepository.findBySlotIdAndDate(slotId, date)
			.orElseThrow(() -> new IllegalStateException("DailySlotCapacity not found"));
		assertThat(before.getRemainingCount()).isEqualTo(10);

		// when
		Reservation saved = reservationService.create(email, req);

		// then
		assertThat(saved.getReservationId()).isNotNull();
		assertThat(saved.getUserId()).isEqualTo(userId);
		assertThat(saved.getSlotId()).isEqualTo(slotId);
		assertThat(saved.getVisitAt()).isEqualTo(visitAt);
		assertThat(saved.getPartySize()).isEqualTo(partySize);
		assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

		DailySlotCapacity after = dailySlotCapacityRepository.findBySlotIdAndDate(slotId, date)
			.orElseThrow(() -> new IllegalStateException("DailySlotCapacity not found"));
		assertThat(after.getRemainingCount()).isEqualTo(8);

		// 저장된 예약이 실제로 CONFIRMED로 존재하는지
		assertThat(reservationRepository.existsByUserIdAndVisitAtAndStatus(userId, visitAt,
			ReservationStatus.CONFIRMED)).isTrue();
	}

	@Test
	@DisplayName("중복 예약 실패")
	void create_fail_duplicatedTime_preCheck() {
		// given
		// 첫 번째 예약
		saveCapacity(slotId, date, 10);
		reservationService.create(email, new ReservationRequestDto(slotId, date, 2, ""));

		// when & then
		// 동일한 조건으로 다시 예약 (동일 유저, 같은 날짜)
		assertThatThrownBy(() -> reservationService.create(email, new ReservationRequestDto(slotId, date, 2, "")))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> {
				ReservationException re = (ReservationException)ex;
				assertThat(re.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_DUPLICATED_TIME);
			});

		DailySlotCapacity after = dailySlotCapacityRepository.findBySlotIdAndDate(slotId, date)
			.orElseThrow(() -> new IllegalStateException("DailySlotCapacity not found"));
		assertThat(after.getRemainingCount()).isEqualTo(8); // 첫 번째 예약에서만 2명이 차감된다.
	}

	@Test
	@DisplayName("좌석 부족 실패")
	void create_fail_capacityNotEnough() {
		// given
		DailySlotCapacity capacity = DailySlotCapacity.builder()
			.slotId(slotId)
			.date(date)
			.remainingCount(1) // remainingCount를 1로 만들어서 준비
			.version(0L)
			.build();
		dailySlotCapacityRepository.save(capacity);

		int partySize = 5; // 실제 요청은 5

		// when & then
		assertThatThrownBy(
			() -> reservationService.create(email, new ReservationRequestDto(slotId, date, partySize, "")))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> {
				ReservationException re = (ReservationException)ex;
				assertThat(re.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_CAPACITY_NOT_ENOUGH);
			});

		// 실패면 예약 저장이 없어야 하고, capacity는 그대로(1)여야 함
		DailySlotCapacity after = dailySlotCapacityRepository.findBySlotIdAndDate(slotId, date)
			.orElseThrow(() -> new IllegalStateException("DailySlotCapacity not found"));
		assertThat(after.getRemainingCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("슬롯 미오픈 실패 - capacity row 없음 (RESERVATION_SLOT_NOT_OPENED)")
	void create_fail_slotNotOpened() {
		// given
		// capacity를 저장하지 않는다 = 미오픈 상태
		ReservationRequestDto req = new ReservationRequestDto(slotId, date, 2, "");

		// when & then
		assertThatThrownBy(() -> reservationService.create(email, req))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> {
				ReservationException re = (ReservationException)ex;
				assertThat(re.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_SLOT_NOT_OPENED);
			});
	}

	private void saveCapacity(Long slotId, LocalDate date, int remainingCount) {
		DailySlotCapacity capacity = DailySlotCapacity.builder()
			.slotId(slotId)
			.date(date)
			.remainingCount(remainingCount)
			.version(0L)
			.build();

		dailySlotCapacityRepository.save(capacity);
	}
}
