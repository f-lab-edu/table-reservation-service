package com.reservation.tablereservationservice.application.reservation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
import com.reservation.tablereservationservice.fixture.RestaurantSlotFixture;
import com.reservation.tablereservationservice.fixture.UserFixture;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.global.exception.UserException;
import com.reservation.tablereservationservice.presentation.common.PageResponseDto;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationListResponseDto;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationSearchDto;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

	private static final LocalDate TEST_DATE = LocalDate.of(2026, 1, 26);
	private static final LocalTime TEST_TIME = LocalTime.of(19, 0);
	private static final LocalDateTime TEST_VISIT_AT = LocalDateTime.of(TEST_DATE, TEST_TIME);
	private static final Pageable PAGEABLE = PageRequest.of(0, 10);

	@Mock
	private UserRepository userRepository;

	@Mock
	private RestaurantSlotRepository restaurantSlotRepository;

	@Mock
	private DailySlotCapacityRepository dailySlotCapacityRepository;

	@Mock
	private ReservationRepository reservationRepository;

	@Mock
	private RestaurantRepository restaurantRepository;

	@InjectMocks
	private ReservationService reservationService;

	private User customer;
	private User owner;
	private RestaurantSlot restaurantSlot;

	@BeforeEach
	void setUp() {
		customer = UserFixture.customer().build();
		owner = UserFixture.owner().build();
		restaurantSlot = RestaurantSlotFixture.slot()
			.time(TEST_TIME)
			.build();
	}

	@Test
	@DisplayName("예약 요청 성공 - CONFIRMED 저장 + capacity 차감")
	void create_success() {
		// given
		DailySlotCapacity capacity = DailySlotCapacityFixture.capacity()
			.slotId(restaurantSlot.getSlotId())
			.date(TEST_DATE)
			.remainingCount(10)
			.build();

		int partySize = 2;

		ReservationRequestDto req = new ReservationRequestDto(restaurantSlot.getSlotId(), TEST_DATE, partySize, "note");

		given(userRepository.fetchByEmail(customer.getEmail())).willReturn(customer);
		given(restaurantSlotRepository.fetchById(restaurantSlot.getSlotId())).willReturn(restaurantSlot);
		given(reservationRepository.existsByUserIdAndVisitAtAndStatus(
			customer.getUserId(),
			TEST_VISIT_AT,
			ReservationStatus.CONFIRMED
		)).willReturn(false);

		given(dailySlotCapacityRepository.findBySlotIdAndDate(
			restaurantSlot.getSlotId(),
			TEST_DATE
		)).willReturn(Optional.of(capacity));

		given(reservationRepository.save(any(Reservation.class))).willAnswer(inv -> inv.getArgument(0));

		// when
		Reservation saved = reservationService.create(customer.getEmail(), req);

		// then
		assertThat(saved.getUserId()).isEqualTo(customer.getUserId());
		assertThat(saved.getSlotId()).isEqualTo(restaurantSlot.getSlotId());
		assertThat(saved.getVisitAt()).isEqualTo(TEST_VISIT_AT);
		assertThat(saved.getPartySize()).isEqualTo(partySize);
		assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

		// then - save argument 검증
		ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
		verify(reservationRepository).save(reservationCaptor.capture());
		Reservation captured = reservationCaptor.getValue();
		assertThat(captured.getUserId()).isEqualTo(customer.getUserId());
		assertThat(captured.getSlotId()).isEqualTo(restaurantSlot.getSlotId());
		assertThat(captured.getVisitAt()).isEqualTo(TEST_VISIT_AT);
		assertThat(captured.getPartySize()).isEqualTo(partySize);
		assertThat(captured.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

		// then - capacity 차감 검증
		ArgumentCaptor<DailySlotCapacity> capacityCaptor = ArgumentCaptor.forClass(DailySlotCapacity.class);
		verify(dailySlotCapacityRepository).updateRemainingCount(capacityCaptor.capture());
		assertThat(capacityCaptor.getValue().getRemainingCount()).isEqualTo(8);
	}

	@Test
	@DisplayName("예약 요청 실패 - 유저 없음")
	void create_fail_userNotFound() {
		// given
		given(userRepository.fetchByEmail(customer.getEmail()))
			.willThrow(new UserException(ErrorCode.RESOURCE_NOT_FOUND, "User"));

		ReservationRequestDto req = new ReservationRequestDto(restaurantSlot.getSlotId(), TEST_DATE, 2, "");

		// when & then
		assertThatThrownBy(() -> reservationService.create(customer.getEmail(), req))
			.isInstanceOf(UserException.class);

		verifyNoInteractions(restaurantSlotRepository, dailySlotCapacityRepository, reservationRepository);
	}

	@Test
	@DisplayName("예약 요청 실패 - 중복 예약 예외 발생")
	void create_fail_duplicatedTime_preCheck() {
		// given
		given(userRepository.fetchByEmail(customer.getEmail())).willReturn(customer);
		given(restaurantSlotRepository.fetchById(restaurantSlot.getSlotId())).willReturn(restaurantSlot);

		given(reservationRepository.existsByUserIdAndVisitAtAndStatus(
			customer.getUserId(), TEST_VISIT_AT, ReservationStatus.CONFIRMED
		)).willReturn(true);

		ReservationRequestDto req = new ReservationRequestDto(restaurantSlot.getSlotId(), TEST_DATE, 2, "");

		// when & then
		assertThatThrownBy(() -> reservationService.create(customer.getEmail(), req))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
				.isEqualTo(ErrorCode.RESERVATION_DUPLICATED_TIME));

		verifyNoInteractions(dailySlotCapacityRepository);
		verify(reservationRepository, never()).save(any());
	}

	@Test
	@DisplayName("예약 요청 실패 - capacity row 없음(해당 날짜 슬롯 미오픈)")
	void create_fail_slotNotOpened() {
		// given
		given(userRepository.fetchByEmail(customer.getEmail())).willReturn(customer);
		given(restaurantSlotRepository.fetchById(restaurantSlot.getSlotId())).willReturn(restaurantSlot);

		given(reservationRepository.existsByUserIdAndVisitAtAndStatus(
			customer.getUserId(), TEST_VISIT_AT, ReservationStatus.CONFIRMED
		)).willReturn(false);

		given(dailySlotCapacityRepository.findBySlotIdAndDate(restaurantSlot.getSlotId(), TEST_DATE
		)).willReturn(Optional.empty());

		ReservationRequestDto req = new ReservationRequestDto(restaurantSlot.getSlotId(), TEST_DATE, 2, "");

		// when & then
		assertThatThrownBy(() -> reservationService.create(customer.getEmail(), req))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
				.isEqualTo(ErrorCode.RESERVATION_SLOT_NOT_OPENED));

		verify(reservationRepository, never()).save(any());
		verify(dailySlotCapacityRepository, never()).updateRemainingCount(any(DailySlotCapacity.class));
	}

	@Test
	@DisplayName("예약 요청 실패 - 좌석 부족")
	void create_fail_capacityNotEnough() {
		// given
		DailySlotCapacity capacity = DailySlotCapacityFixture.capacity()
			.slotId(restaurantSlot.getSlotId())
			.date(TEST_DATE)
			.remainingCount(1)
			.build();

		given(userRepository.fetchByEmail(customer.getEmail())).willReturn(customer);
		given(restaurantSlotRepository.fetchById(restaurantSlot.getSlotId())).willReturn(restaurantSlot);

		given(reservationRepository.existsByUserIdAndVisitAtAndStatus(
			customer.getUserId(),
			TEST_VISIT_AT,
			ReservationStatus.CONFIRMED
		)).willReturn(false);

		given(dailySlotCapacityRepository.findBySlotIdAndDate(restaurantSlot.getSlotId(), TEST_DATE))
			.willReturn(Optional.of(capacity));

		ReservationRequestDto req = new ReservationRequestDto(restaurantSlot.getSlotId(), TEST_DATE, 2, "");

		// when & then
		assertThatThrownBy(() -> reservationService.create(customer.getEmail(), req))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
				.isEqualTo(ErrorCode.RESERVATION_CAPACITY_NOT_ENOUGH));

		verify(reservationRepository, never()).save(any());
		verify(dailySlotCapacityRepository, never()).updateRemainingCount(any(DailySlotCapacity.class));
	}

	@Test
	@DisplayName("사용자 예약 목록 조회 성공")
	void findMyReservations_success() {
		// given
		ReservationSearchDto searchDto = createSearchDto();

		Long slotId = 10L;
		Long restaurantId = 100L;
		ReservationListFixture fixture = reservationListFixture(customer.getUserId(), slotId, restaurantId);

		given(userRepository.fetchByEmail(customer.getEmail())).willReturn(customer);

		Page<Reservation> confirmedOnlyPage =
			new PageImpl<>(
				fixture.page().getContent().stream()
					.filter(r -> r.getStatus() == ReservationStatus.CONFIRMED)
					.toList(), PAGEABLE, 1
			);

		given(reservationRepository.findMyReservations(
			eq(customer.getUserId()),
			eq(ReservationStatus.CONFIRMED),
			any(),
			any(),
			any()
		)).willReturn(confirmedOnlyPage);

		given(restaurantSlotRepository.findAllById(anyList()))
			.willReturn(List.of(fixture.slot()));

		given(restaurantRepository.findAllById(anyList()))
			.willReturn(List.of(fixture.restaurant()));

		// when
		PageResponseDto<ReservationListResponseDto> result =
			reservationService.findMyReservations(customer.getEmail(), searchDto);

		// then
		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().getFirst().getRestaurantName()).isEqualTo("강남 한상");
		assertThat(result.getContent().getFirst().getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
	}

	@Test
	@DisplayName("점주 예약 목록 조회 성공")
	void findOwnerReservations_success() {
		// given
		ReservationSearchDto searchDto = createSearchDto();

		Long ownerRestaurantId = 2L;
		Long slotId = 10L;

		ReservationListFixture fixture = reservationListFixture(customer.getUserId(), slotId, ownerRestaurantId);

		given(userRepository.fetchByEmail(owner.getEmail())).willReturn(owner);

		given(restaurantRepository.findAllByOwnerId(owner.getUserId()))
			.willReturn(List.of(fixture.restaurant()));

		given(reservationRepository.findOwnerReservations(
			eq(List.of(ownerRestaurantId)),
			eq(ReservationStatus.CONFIRMED),
			any(),
			any(),
			any()
		)).willReturn(fixture.page());

		given(restaurantSlotRepository.findAllById(anyList()))
			.willReturn(List.of(fixture.slot()));

		given(restaurantRepository.findAllById(anyList()))
			.willReturn(List.of(fixture.restaurant()));

		// owner 조회는 예약자 정보가 여러 명이므로 userRepository.findAllById 호출됨
		given(userRepository.findAllById(anyList()))
			.willReturn(List.of(customer));

		// when
		PageResponseDto<ReservationListResponseDto> result =
			reservationService.findOwnerReservations(owner.getEmail(), searchDto);

		// then
		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().getFirst().getRestaurantName()).isEqualTo("강남 한상");
		assertThat(result.getContent().getFirst().getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
	}

	private ReservationSearchDto createSearchDto() {
		ReservationSearchDto searchDto = new ReservationSearchDto();
		searchDto.setFromDate(TEST_DATE);
		searchDto.setToDate(TEST_DATE);
		searchDto.setStatus(ReservationStatus.CONFIRMED);
		searchDto.setPageable(PAGEABLE);
		return searchDto;
	}

	private ReservationListFixture reservationListFixture(
		Long userId,
		Long slotId,
		Long restaurantId
	) {
		Reservation confirmed = Reservation.builder()
			.reservationId(1L)
			.userId(userId)
			.slotId(slotId)
			.visitAt(TEST_VISIT_AT)
			.partySize(2)
			.note("confirmed")
			.status(ReservationStatus.CONFIRMED)
			.build();

		Page<Reservation> page = new PageImpl<>(List.of(confirmed), PAGEABLE, 2);

		RestaurantSlot slot = RestaurantSlot.builder()
			.slotId(slotId)
			.restaurantId(restaurantId)
			.time(TEST_TIME)
			.maxCapacity(10)
			.build();

		Restaurant restaurant = Restaurant.builder()
			.restaurantId(restaurantId)
			.name("강남 한상")
			.build();

		return new ReservationListFixture(page, slot, restaurant);
	}

	private record ReservationListFixture(
		Page<Reservation> page,
		RestaurantSlot slot,
		Restaurant restaurant
	) {
	}

}
