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

	private static final LocalDate BASE_DATE = LocalDate.of(2030, 1, 1);
	private static final LocalTime BASE_TIME = LocalTime.of(19, 0);
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
	private Restaurant restaurant;
	private RestaurantSlot restaurantSlot;

	@BeforeEach
	void setUp() {
		customer = userRepository.save(UserFixture.customer().build());
		owner = userRepository.save(UserFixture.owner().build());

		restaurant = restaurantRepository.save(
			RestaurantFixture.restaurant()
				.ownerId(owner.getUserId())
				.build()
		);

		restaurantSlot = restaurantSlotRepository.save(
			RestaurantSlotFixture.slot()
				.restaurantId(restaurant.getRestaurantId())
				.time(BASE_TIME)
				.maxCapacity(10)
				.build()
		);
	}

	@Test
	@DisplayName("예약 생성 성공 - CONFIRMED 저장 + capacity 차감")
	void create_success_confirmed_and_decreaseCapacity() {
		// given
		LocalDate date = BASE_DATE;
		int partySize = 2;

		saveCapacity(restaurantSlot.getSlotId(), date, 10);

		ReservationRequestDto req = createReservationRequest(restaurantSlot.getSlotId(), date, partySize, "note");

		// when
		Reservation saved = reservationService.create(customer.getEmail(), req);

		// then
		assertThat(saved.getReservationId()).isNotNull();
		assertThat(saved.getUserId()).isEqualTo(customer.getUserId());
		assertThat(saved.getSlotId()).isEqualTo(restaurantSlot.getSlotId());
		assertThat(saved.getVisitAt()).isEqualTo(date.atTime(restaurantSlot.getTime()));
		assertThat(saved.getPartySize()).isEqualTo(partySize);
		assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

		DailySlotCapacity after = dailySlotCapacityRepository.findBySlotIdAndDate(restaurantSlot.getSlotId(), date)
			.orElseThrow(() -> new IllegalStateException("DailySlotCapacity not found"));
		assertThat(after.getRemainingCount()).isEqualTo(8);

		assertThat(reservationRepository.existsByUserIdAndVisitAtAndStatus(
			customer.getUserId(),
			date.atTime(restaurantSlot.getTime()),
			ReservationStatus.CONFIRMED
		)).isTrue();
	}

	@Test
	@DisplayName("중복 예약 실패 - 같은 유저/같은 방문시각")
	void create_fail_duplicatedTime_preCheck() {
		// given
		LocalDate date = BASE_DATE;
		saveCapacity(restaurantSlot.getSlotId(), date, 10);

		ReservationRequestDto firstReq = createReservationRequest(restaurantSlot.getSlotId(), date, 2, "");
		ReservationRequestDto secondReq = createReservationRequest(restaurantSlot.getSlotId(), date, 2, "");

		reservationService.create(customer.getEmail(), firstReq);

		// when & then
		assertThatThrownBy(() -> reservationService.create(customer.getEmail(), secondReq))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
				.isEqualTo(ErrorCode.RESERVATION_DUPLICATED_TIME));

		DailySlotCapacity after = dailySlotCapacityRepository.findBySlotIdAndDate(restaurantSlot.getSlotId(), date)
			.orElseThrow(() -> new IllegalStateException("DailySlotCapacity not found"));
		assertThat(after.getRemainingCount()).isEqualTo(8); // 첫 번째 예약만 차감
	}

	@Test
	@DisplayName("좌석 부족 실패 - capacity 부족이면 예약 미생성 + capacity 유지")
	void create_fail_capacityNotEnough() {
		// given
		LocalDate date = BASE_DATE;
		int partySize = 5;

		saveCapacity(restaurantSlot.getSlotId(), date, 1); // remainingCount를 1로 만들어서 준비

		ReservationRequestDto req = createReservationRequest(restaurantSlot.getSlotId(), date, partySize, "");

		// when & then
		assertThatThrownBy(() -> reservationService.create(customer.getEmail(), req))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
				.isEqualTo(ErrorCode.RESERVATION_CAPACITY_NOT_ENOUGH));

		DailySlotCapacity after = dailySlotCapacityRepository.findBySlotIdAndDate(restaurantSlot.getSlotId(), date)
			.orElseThrow(() -> new IllegalStateException("DailySlotCapacity not found"));
		assertThat(after.getRemainingCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("슬롯 미오픈 실패 - capacity row 없음")
	void create_fail_slotNotOpened() {
		// given
		// capacity를 저장하지 않는다 = 미오픈 상태
		ReservationRequestDto req = createReservationRequest(restaurantSlot.getSlotId(), BASE_DATE, 2, "");

		// when & then
		assertThatThrownBy(() -> reservationService.create(customer.getEmail(), req))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
				.isEqualTo(ErrorCode.RESERVATION_SLOT_NOT_OPENED));
	}

	@Test
	@DisplayName("사용자 예약 목록 조회 성공 - status/기간 조건에 따라 필터링된다")
	void findMyReservations_success_filteringByStatusAndPeriod() {
		// given
		LocalDate inRangeDate = BASE_DATE;
		LocalDate outRangeDate = BASE_DATE.minusDays(10);

		saveCapacity(restaurantSlot.getSlotId(), inRangeDate, 10);
		saveCapacity(restaurantSlot.getSlotId(), outRangeDate, 10);

		ReservationRequestDto inRangeReq =
			createReservationRequest(restaurantSlot.getSlotId(), inRangeDate, 2, "note");
		ReservationRequestDto outRangeReq =
			createReservationRequest(restaurantSlot.getSlotId(), outRangeDate, 2, "note");

		Reservation inRange = reservationService.create(customer.getEmail(), inRangeReq);
		Reservation outRange = reservationService.create(customer.getEmail(), outRangeReq);

		// outRange를 취소해서 status 필터도 같이 검증
		reservationService.cancel(customer.getEmail(), outRange.getReservationId());

		ReservationSearchDto searchDto = createSearchDto(
			BASE_DATE.minusDays(3),
			BASE_DATE.plusDays(1),
			ReservationStatus.CONFIRMED
		);

		// when
		PageResponseDto<ReservationListResponseDto> result =
			reservationService.findMyReservations(customer.getEmail(), searchDto);

		// then
		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().getFirst().getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(result.getContent().getFirst().getVisitAt()).isEqualTo(inRange.getVisitAt());
	}

	@Test
	@DisplayName("사용자 예약 목록 조회 - status=CONFIRMED면 취소된 예약은 제외된다")
	void findMyReservations_success_onlyConfirmed_whenStatusConfirmed() {
		// given
		LocalDate date = BASE_DATE;

		saveCapacity(restaurantSlot.getSlotId(), date, 10);
		saveCapacity(restaurantSlot.getSlotId(), date.plusDays(1), 10);

		ReservationRequestDto confirmedReq =
			createReservationRequest(restaurantSlot.getSlotId(), date, 2, "note-confirmed");
		ReservationRequestDto canceledReq =
			createReservationRequest(restaurantSlot.getSlotId(), date.plusDays(1), 2, "note-canceled");

		Reservation confirmed = reservationService.create(customer.getEmail(), confirmedReq);
		Reservation willBeCanceled = reservationService.create(customer.getEmail(), canceledReq);
		reservationService.cancel(customer.getEmail(), willBeCanceled.getReservationId());

		ReservationSearchDto searchDto = createSearchDto(
			BASE_DATE.minusDays(1),
			BASE_DATE.plusDays(2),
			ReservationStatus.CONFIRMED
		);

		// when
		PageResponseDto<ReservationListResponseDto> result =
			reservationService.findMyReservations(customer.getEmail(), searchDto);

		// then
		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().getFirst().getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(result.getContent().getFirst().getVisitAt()).isEqualTo(confirmed.getVisitAt());
	}

	@Test
	@DisplayName("사용자 예약 목록 조회 - status=null이면 확정/취소 모두 조회된다")
	void findMyReservations_success_allStatuses_whenStatusNull() {
		// given
		LocalDate date = BASE_DATE;

		saveCapacity(restaurantSlot.getSlotId(), date, 10);
		saveCapacity(restaurantSlot.getSlotId(), date.plusDays(1), 10);

		ReservationRequestDto confirmedReq =
			createReservationRequest(restaurantSlot.getSlotId(), date, 2, "confirmed");
		ReservationRequestDto canceledReq =
			createReservationRequest(restaurantSlot.getSlotId(), date.plusDays(1), 2, "canceled");

		reservationService.create(customer.getEmail(), confirmedReq);

		Reservation canceledTarget = reservationService.create(customer.getEmail(), canceledReq);
		reservationService.cancel(customer.getEmail(), canceledTarget.getReservationId());

		ReservationSearchDto searchDto = createSearchDto(
			BASE_DATE.minusDays(1),
			BASE_DATE.plusDays(2),
			null
		);

		// when
		PageResponseDto<ReservationListResponseDto> result =
			reservationService.findMyReservations(customer.getEmail(), searchDto);

		// then
		assertThat(result.getContent()).hasSize(2);
		assertThat(result.getContent())
			.extracting(ReservationListResponseDto::getStatus)
			.containsExactlyInAnyOrder(ReservationStatus.CONFIRMED, ReservationStatus.CANCELED);
	}

	@Test
	@DisplayName("점주 예약 목록 조회 성공 - owner의 가게 예약 1건 조회된다")
	void findOwnerReservations_success_singleReservation() {
		// given
		LocalDate date = BASE_DATE;

		saveCapacity(restaurantSlot.getSlotId(), date, 10);
		ReservationRequestDto req = createReservationRequest(restaurantSlot.getSlotId(), date, 2, "note");

		reservationService.create(customer.getEmail(), req);

		ReservationSearchDto searchDto = createSearchDto(
			BASE_DATE.minusDays(1),
			BASE_DATE.plusDays(1),
			ReservationStatus.CONFIRMED
		);

		// when
		PageResponseDto<ReservationListResponseDto> result =
			reservationService.findOwnerReservations(owner.getEmail(), searchDto);

		// then
		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().getFirst().getRestaurantName()).isEqualTo(restaurant.getName());
		assertThat(result.getContent().getFirst().getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
	}

	@Test
	@DisplayName("예약 취소 성공 - 상태 CANCELED 저장 + capacity 복구")
	void cancel_success_canceled_and_restoreCapacity() {
		// given
		Long slotId = restaurantSlot.getSlotId();
		String email = customer.getEmail();
		int partySize = 2;

		LocalDate visitDate = BASE_DATE.plusDays(3);

		saveCapacity(slotId, visitDate, 10);

		ReservationRequestDto req = createReservationRequest(slotId, visitDate, partySize, "note");
		Reservation created = reservationService.create(email, req);

		// 생성 시점 capacity가 10 -> 8로 감소했는지 확인
		DailySlotCapacity afterCreate = dailySlotCapacityRepository.findBySlotIdAndDate(slotId, visitDate)
			.orElseThrow(() -> new IllegalStateException("DailySlotCapacity not found"));
		assertThat(afterCreate.getRemainingCount()).isEqualTo(8);

		// when
		Reservation canceled = reservationService.cancel(email, created.getReservationId());

		// then
		assertThat(canceled.getStatus()).isEqualTo(ReservationStatus.CANCELED);

		// DB에 실제 반영됐는지 재조회로 확인
		Reservation stored = reservationRepository.findById(created.getReservationId())
			.orElseThrow(() -> new IllegalStateException("Reservation not found"));
		assertThat(stored.getStatus()).isEqualTo(ReservationStatus.CANCELED);

		DailySlotCapacity afterCancel = dailySlotCapacityRepository.findBySlotIdAndDate(slotId, visitDate)
			.orElseThrow(() -> new IllegalStateException("DailySlotCapacity not found"));
		assertThat(afterCancel.getRemainingCount()).isEqualTo(10);    // capacity 복구(8 -> 10)
	}

	@Test
	@DisplayName("예약 취소 실패 - 다른 유저 예약 취소 시도")
	void cancel_fail_forbidden() {
		// given
		Long slotId = restaurantSlot.getSlotId();
		int partySize = 2;
		LocalDate visitDate = BASE_DATE.plusDays(3);

		saveCapacity(slotId, visitDate, 10);

		ReservationRequestDto req = createReservationRequest(slotId, visitDate, partySize, "note");
		Reservation created = reservationService.create(customer.getEmail(), req);

		User other = userRepository.save(
			UserFixture.customer()
				.userId(3L)
				.email("other@test.com")
				.phone("010-0000-9999")
				.build()
		);

		// when & then
		assertThatThrownBy(() -> reservationService.cancel(other.getEmail(), created.getReservationId()))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
				.isEqualTo(ErrorCode.RESERVATION_FORBIDDEN));
	}

	@Test
	@DisplayName("예약 취소 실패 - 이미 취소된 예약")
	void cancel_fail_alreadyCanceled() {
		// given
		LocalDate visitDate = BASE_DATE.plusDays(3);
		Long slotId = restaurantSlot.getSlotId();
		int partySize = 2;

		saveCapacity(slotId, visitDate, 10);

		ReservationRequestDto req = createReservationRequest(slotId, visitDate, partySize, "note");
		Reservation created = reservationService.create(customer.getEmail(), req);

		// 한 번 취소 성공
		reservationService.cancel(customer.getEmail(), created.getReservationId());

		// when & then (두 번째 취소)
		assertThatThrownBy(() -> reservationService.cancel(customer.getEmail(), created.getReservationId()))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
				.isEqualTo(ErrorCode.RESERVATION_ALREADY_CANCELED));
	}

	@Test
	@DisplayName("예약 취소 실패 - 취소 가능 기한(24시간) 지남")
	void cancel_fail_deadlinePassed() {
		// given
		Long slotId = restaurantSlot.getSlotId();
		int partySize = 2;

		// visitAt이 현재로부터 24시간 이내면 취소 불가
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime visitAt = now.plusHours(23);
		LocalDate date = visitAt.toLocalDate();

		saveCapacity(slotId, date, 8);

		ReservationRequestDto req = createReservationRequest(slotId, date, partySize, "note");
		Reservation created = reservationService.create(customer.getEmail(), req);
		Long reservationId = created.getReservationId();

		// when & then
		assertThatThrownBy(() -> reservationService.cancel(customer.getEmail(), reservationId))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
				.isEqualTo(ErrorCode.RESERVATION_CANCEL_DEADLINE_PASSED));
	}

	private void saveCapacity(Long slotId, LocalDate date, int remainingCount) {
		dailySlotCapacityRepository.save(
			DailySlotCapacityFixture.capacity()
				.slotId(slotId)
				.date(date)
				.remainingCount(remainingCount)
				.build()
		);
	}

	private ReservationSearchDto createSearchDto(LocalDate fromDate, LocalDate toDate, ReservationStatus status) {
		ReservationSearchDto searchDto = new ReservationSearchDto();
		searchDto.setFromDate(fromDate);
		searchDto.setToDate(toDate);
		searchDto.setStatus(status);
		searchDto.setPageable(PAGEABLE);
		return searchDto;
	}

	private ReservationRequestDto createReservationRequest(Long slotId, LocalDate date, int partySize, String note) {
		return new ReservationRequestDto(slotId, date, partySize, note);
	}
}
