package com.reservation.tablereservationservice.application.reservation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacity;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantRepository;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRepository;
import com.reservation.tablereservationservice.fixture.DailySlotCapacityFixture;
import com.reservation.tablereservationservice.fixture.ReservationFixture;
import com.reservation.tablereservationservice.fixture.RestaurantSlotFixture;
import com.reservation.tablereservationservice.fixture.UserFixture;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

	private static final LocalDate BASE_DATE = LocalDate.of(2030, 1, 1);
	private static final LocalTime BASE_TIME = LocalTime.of(19, 0);

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
	private RestaurantSlot restaurantSlot;

	@BeforeEach
	void setUp() {
		customer = UserFixture
			.customer()
			.userId(1L)
			.build();

		restaurantSlot = RestaurantSlotFixture.slot()
			.slotId(100L)
			.time(BASE_TIME)
			.build();
	}

	@Test
	@DisplayName("예약 요청 성공 - CONFIRMED 저장 + capacity 차감")
	void create_success() {
		// given
		LocalDate date = BASE_DATE;
		LocalDateTime visitAt = LocalDateTime.of(date, restaurantSlot.getTime());
		int partySize = 2;

		DailySlotCapacity capacity = DailySlotCapacityFixture.capacity()
			.slotId(restaurantSlot.getSlotId())
			.date(date)
			.remainingCount(10)
			.build();

		ReservationRequestDto req = new ReservationRequestDto(restaurantSlot.getSlotId(), date, partySize, "note");

		given(userRepository.fetchByEmail(customer.getEmail())).willReturn(customer);
		given(restaurantSlotRepository.fetchById(restaurantSlot.getSlotId())).willReturn(restaurantSlot);
		given(reservationRepository.existsByUserIdAndVisitAtAndStatus(
			customer.getUserId(), visitAt, ReservationStatus.CONFIRMED
		)).willReturn(false);

		given(dailySlotCapacityRepository.findBySlotIdAndDate(restaurantSlot.getSlotId(), date))
			.willReturn(Optional.of(capacity));

		given(reservationRepository.save(any(Reservation.class))).willAnswer(inv -> inv.getArgument(0));

		// when
		Reservation saved = reservationService.create(customer.getEmail(), req);

		// then (반환값은 핵심만 확인)
		assertThat(saved.getUserId()).isEqualTo(customer.getUserId());
		assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

		verify(reservationRepository).save(any(Reservation.class));

		// capacity 차감 확인
		ArgumentCaptor<DailySlotCapacity> capacityCaptor = ArgumentCaptor.forClass(DailySlotCapacity.class);
		verify(dailySlotCapacityRepository).updateRemainingCount(capacityCaptor.capture());
		assertThat(capacityCaptor.getValue().getRemainingCount()).isEqualTo(8);
	}

	@Test
	@DisplayName("예약 요청 실패 - 좌석이 부족하면 409 예외가 발생한다.")
	void create_fail_capacityNotEnough() {
		// given
		LocalDate date = BASE_DATE;
		LocalDateTime visitAt = LocalDateTime.of(date, restaurantSlot.getTime());

		DailySlotCapacity capacity = DailySlotCapacityFixture.capacity()
			.slotId(restaurantSlot.getSlotId())
			.date(date)
			.remainingCount(1)
			.build();

		ReservationRequestDto req = new ReservationRequestDto(restaurantSlot.getSlotId(), date, 2, "");

		given(userRepository.fetchByEmail(customer.getEmail())).willReturn(customer);
		given(restaurantSlotRepository.fetchById(restaurantSlot.getSlotId())).willReturn(restaurantSlot);
		given(reservationRepository.existsByUserIdAndVisitAtAndStatus(
			customer.getUserId(), visitAt, ReservationStatus.CONFIRMED
		)).willReturn(false);

		given(dailySlotCapacityRepository.findBySlotIdAndDate(restaurantSlot.getSlotId(), date))
			.willReturn(Optional.of(capacity));

		// when & then
		assertThatThrownBy(() -> reservationService.create(customer.getEmail(), req))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
				.isEqualTo(ErrorCode.RESERVATION_CAPACITY_NOT_ENOUGH));

		verify(reservationRepository, never()).save(any());
		verify(dailySlotCapacityRepository, never()).updateRemainingCount(any(DailySlotCapacity.class));
	}

	@Test
	@DisplayName("예약 취소 실패 - 본인 예약이 아니면 403 예외가 발생한다.")
	void cancel_fail_forbidden() {
		// given
		Long reservationId = 999L;
		LocalDateTime now = LocalDateTime.now(); // now는 테스트에서 한 번만
		LocalDateTime visitAt = now.plusDays(2);

		Reservation reservation = ReservationFixture.confirmed()
			.reservationId(reservationId)
			.userId(2L) // 다른 유저 예약
			.slotId(restaurantSlot.getSlotId())
			.visitAt(visitAt)
			.partySize(2)
			.build();

		given(userRepository.fetchByEmail(customer.getEmail())).willReturn(customer);
		given(reservationRepository.fetchById(reservationId)).willReturn(reservation);

		// when & then
		assertThatThrownBy(() -> reservationService.cancel(customer.getEmail(), reservationId))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
				.isEqualTo(ErrorCode.RESERVATION_FORBIDDEN));

		verifyNoInteractions(dailySlotCapacityRepository);
		verify(reservationRepository, never()).updateStatus(any());
	}

	@Test
	@DisplayName("예약 취소 실패 - 이미 취소된 예약이면 400 예외가 발생한다.")
	void cancel_fail_alreadyCanceled() {
		// given
		Long reservationId = 999L;
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime visitAt = now.plusDays(2);

		Reservation reservation = ReservationFixture.canceled()
			.reservationId(reservationId)
			.userId(customer.getUserId())
			.slotId(restaurantSlot.getSlotId())
			.visitAt(visitAt)
			.partySize(2)
			.build();

		given(userRepository.fetchByEmail(customer.getEmail())).willReturn(customer);
		given(reservationRepository.fetchById(reservationId)).willReturn(reservation);

		// when & then
		assertThatThrownBy(() -> reservationService.cancel(customer.getEmail(), reservationId))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
				.isEqualTo(ErrorCode.RESERVATION_ALREADY_CANCELED));

		verifyNoInteractions(dailySlotCapacityRepository);
		verify(reservationRepository, never()).updateStatus(any());
	}

	@Test
	@DisplayName("예약 취소 실패 - 취소 가능 기한(24시간)이 지나면 400 예외가 발생한다.")
	void cancel_fail_deadlinePassed() {
		// given
		Long reservationId = 999L;
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime visitAt = now.plusHours(23);

		Reservation reservation = ReservationFixture.confirmed()
			.reservationId(reservationId)
			.userId(customer.getUserId())
			.slotId(restaurantSlot.getSlotId())
			.visitAt(visitAt)
			.partySize(2)
			.build();

		given(userRepository.fetchByEmail(customer.getEmail())).willReturn(customer);
		given(reservationRepository.fetchById(reservationId)).willReturn(reservation);

		// when & then
		assertThatThrownBy(() -> reservationService.cancel(customer.getEmail(), reservationId))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
				.isEqualTo(ErrorCode.RESERVATION_CANCEL_DEADLINE_PASSED));

		verifyNoInteractions(dailySlotCapacityRepository);
		verify(reservationRepository, never()).updateStatus(any());
	}
}
