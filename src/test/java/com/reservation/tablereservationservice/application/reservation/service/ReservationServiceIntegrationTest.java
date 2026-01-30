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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
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
import com.reservation.tablereservationservice.fixture.DailySlotCapacityFixture;
import com.reservation.tablereservationservice.fixture.RestaurantFixture;
import com.reservation.tablereservationservice.fixture.RestaurantSlotFixture;
import com.reservation.tablereservationservice.fixture.UserFixture;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.presentation.common.PageResponseDto;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationListResponseDto;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationSearchDto;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReservationServiceIntegrationTest {

	private static final LocalDate TEST_DATE = LocalDate.of(2026, 1, 26);
	private static final LocalTime TEST_TIME = LocalTime.of(19, 0);
	private static final LocalDateTime TEST_VISIT_AT = LocalDateTime.of(TEST_DATE, TEST_TIME);
	private static final Pageable PAGEABLE = PageRequest.of(0, 10);

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

	private User customer;
	private User owner;
	private RestaurantSlot restaurantSlot;

	@BeforeEach
	void setUp() {
		customer = userRepository.save(
			UserFixture.customer().build()
		);

		owner = userRepository.save(
			UserFixture.owner().build()
		);

		Restaurant restaurant = restaurantRepository.save(
			RestaurantFixture.restaurant()
				.ownerId(owner.getUserId())
				.build()
		);

		restaurantSlot = restaurantSlotRepository.save(
			RestaurantSlotFixture.slot()
				.restaurantId(restaurant.getRestaurantId())
				.time(TEST_TIME)
				.maxCapacity(10)
				.build()
		);
	}

	@Test
	@DisplayName("예약 생성 성공 - CONFIRMED 저장 + capacity 차감")
	void create_success_confirmed_and_decreaseCapacity() {
		// given
		int partySize = 2;
		dailySlotCapacityRepository.save(
			DailySlotCapacityFixture.capacity()
				.slotId(restaurantSlot.getSlotId())
				.date(TEST_DATE)
				.remainingCount(10)
				.build()
		);

		ReservationRequestDto req = new ReservationRequestDto(restaurantSlot.getSlotId(), TEST_DATE, partySize, "note");

		// when
		Reservation saved = reservationService.create(customer.getEmail(), req);

		// then
		assertThat(saved.getReservationId()).isNotNull();
		assertThat(saved.getUserId()).isEqualTo(customer.getUserId());
		assertThat(saved.getSlotId()).isEqualTo(restaurantSlot.getSlotId());
		assertThat(saved.getVisitAt()).isEqualTo(TEST_VISIT_AT);
		assertThat(saved.getPartySize()).isEqualTo(partySize);
		assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

		DailySlotCapacity after = dailySlotCapacityRepository.findBySlotIdAndDate(restaurantSlot.getSlotId(), TEST_DATE)
			.orElseThrow(() -> new IllegalStateException("DailySlotCapacity not found"));
		assertThat(after.getRemainingCount()).isEqualTo(8);

		// 저장된 예약이 실제로 CONFIRMED로 존재하는지
		assertThat(reservationRepository.existsByUserIdAndVisitAtAndStatus(
			customer.getUserId(),
			TEST_VISIT_AT,
			ReservationStatus.CONFIRMED
		)).isTrue();
	}

	@Test
	@DisplayName("중복 예약 실패")
	void create_fail_duplicatedTime_preCheck() {
		// given
		dailySlotCapacityRepository.save(
			DailySlotCapacityFixture.capacity()
				.slotId(restaurantSlot.getSlotId())
				.date(TEST_DATE)
				.remainingCount(10)
				.build()
		);

		reservationService.create(
			customer.getEmail(),
			new ReservationRequestDto(restaurantSlot.getSlotId(), TEST_DATE, 2, "")
		);

		// when & then
		// 동일한 조건으로 다시 예약 (동일 유저, 같은 날짜)
		assertThatThrownBy(() -> reservationService.create(
			customer.getEmail(),
			new ReservationRequestDto(restaurantSlot.getSlotId(), TEST_DATE, 2, "")
		))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> {
				ReservationException re = (ReservationException)ex;
				assertThat(re.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_DUPLICATED_TIME);
			});

		DailySlotCapacity after = dailySlotCapacityRepository.findBySlotIdAndDate(restaurantSlot.getSlotId(), TEST_DATE)
			.orElseThrow(() -> new IllegalStateException("DailySlotCapacity not found"));
		assertThat(after.getRemainingCount()).isEqualTo(8); // 첫 번째 예약에서만 2명이 차감된다.
	}

	@Test
	@DisplayName("좌석 부족 실패")
	void create_fail_capacityNotEnough() {
		// given
		dailySlotCapacityRepository.save(
			DailySlotCapacityFixture.capacity()
				.slotId(restaurantSlot.getSlotId())
				.date(TEST_DATE)
				.remainingCount(1) // remainingCount를 1로 만들어서 준비
				.build()
		);

		int partySize = 5; // 실제 요청은 5

		// when & then
		assertThatThrownBy(
			() -> reservationService.create(
				customer.getEmail(),
				new ReservationRequestDto(restaurantSlot.getSlotId(), TEST_DATE, partySize, "")
			))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> {
				ReservationException re = (ReservationException)ex;
				assertThat(re.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_CAPACITY_NOT_ENOUGH);
			});

		// 실패면 예약 저장이 없어야 하고, capacity는 그대로(1)여야 함
		DailySlotCapacity after = dailySlotCapacityRepository.findBySlotIdAndDate(restaurantSlot.getSlotId(), TEST_DATE)
			.orElseThrow(() -> new IllegalStateException("DailySlotCapacity not found"));
		assertThat(after.getRemainingCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("슬롯 미오픈 실패 - capacity row 없음 (RESERVATION_SLOT_NOT_OPENED)")
	void create_fail_slotNotOpened() {
		// given
		// capacity를 저장하지 않는다 = 미오픈 상태
		ReservationRequestDto req = new ReservationRequestDto(restaurantSlot.getSlotId(), TEST_DATE, 2, "");

		// when & then
		assertThatThrownBy(() -> reservationService.create(customer.getEmail(), req))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> {
				ReservationException re = (ReservationException)ex;
				assertThat(re.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_SLOT_NOT_OPENED);
			});
	}

	@Test
	@DisplayName("사용자 예약 목록 조회 성공 - 1건")
	void findMyReservations_success() {
		// given
		dailySlotCapacityRepository.save(
			DailySlotCapacityFixture.capacity()
				.slotId(restaurantSlot.getSlotId())
				.date(TEST_DATE)
				.remainingCount(10)
				.build()
		);

		ReservationSearchDto searchDto = new ReservationSearchDto();
		searchDto.setFromDate(TEST_DATE);
		searchDto.setToDate(TEST_DATE);
		searchDto.setStatus(ReservationStatus.CONFIRMED);
		searchDto.setPageable(PAGEABLE);

		// when
		PageResponseDto<ReservationListResponseDto> result =
			reservationService.findMyReservations(customer.getEmail(), searchDto);

		// then
		assertThat(result.getContent()).hasSize(1);
	}

	@Test
	@DisplayName("점주 예약 목록 조회 성공 - 1건")
	void findOwnerReservations_success() {
		// given
		dailySlotCapacityRepository.save(
			DailySlotCapacityFixture.capacity()
				.slotId(restaurantSlot.getSlotId())
				.date(TEST_DATE)
				.remainingCount(10)
				.build()
		);

		reservationService.create(
			customer.getEmail(),
			new ReservationRequestDto(restaurantSlot.getSlotId(), TEST_DATE, 2, "note")
		);

		ReservationSearchDto searchDto = new ReservationSearchDto();
		searchDto.setFromDate(TEST_DATE);
		searchDto.setToDate(TEST_DATE);
		searchDto.setStatus(ReservationStatus.CONFIRMED);
		searchDto.setPageable(PAGEABLE);

		// when
		PageResponseDto<ReservationListResponseDto> result =
			reservationService.findOwnerReservations(owner.getEmail(), searchDto);

		// then
		assertThat(result.getContent()).hasSize(1);
	}
}
