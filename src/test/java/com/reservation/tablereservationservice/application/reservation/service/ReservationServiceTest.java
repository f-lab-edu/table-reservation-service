package com.reservation.tablereservationservice.application.reservation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

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
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRepository;
import com.reservation.tablereservationservice.domain.user.UserRole;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.global.exception.RestaurantException;
import com.reservation.tablereservationservice.global.exception.UserException;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private RestaurantSlotRepository restaurantSlotRepository;

	@Mock
	private DailySlotCapacityRepository dailySlotCapacityRepository;

	@Mock
	private ReservationRepository reservationRepository;

	@InjectMocks
	private ReservationService reservationService;

	@Test
	@DisplayName("예약 생성 성공 - CONFIRMED 저장 + capacity 차감")
	void create_success() {
		// given
		String email = "customer01@test.com";
		LocalDate date = LocalDate.of(2026, 1, 26);
		LocalTime slotTime = LocalTime.of(19, 0);
		Long userId = 1L;
		Long slotId = 10L;
		Long restaurantId = 100L;
		int partySize = 2;

		User user = createTestUser(userId, email);
		RestaurantSlot slot = createTestSlot(slotId, restaurantId, slotTime);

		DailySlotCapacity capacity = DailySlotCapacity.builder()
			.capacityId(777L)
			.slotId(slotId)
			.date(date)
			.remainingCount(10)
			.version(0L)
			.build();

		ReservationRequestDto req = new ReservationRequestDto(slotId, date, partySize, "note");

		given(userRepository.fetchByEmail(email)).willReturn(user);
		given(restaurantSlotRepository.fetchById(slotId)).willReturn(slot);
		given(reservationRepository.existsByUserIdAndVisitAtAndStatus(userId, LocalDateTime.of(date, slotTime),
			ReservationStatus.CONFIRMED)).willReturn(false);
		given(dailySlotCapacityRepository.findBySlotIdAndDate(slotId, date)).willReturn(Optional.of(capacity));
		given(reservationRepository.save(any(Reservation.class))).willAnswer(inv -> {
			Reservation r = inv.getArgument(0);
			return Reservation.builder()
				.reservationId(999L)
				.userId(r.getUserId())
				.slotId(r.getSlotId())
				.visitAt(r.getVisitAt())
				.partySize(r.getPartySize())
				.note(r.getNote())
				.status(r.getStatus())
				.build();
		});

		// when
		Reservation saved = reservationService.create(email, req);

		// then
		assertThat(saved.getReservationId()).isEqualTo(999L);
		assertThat(saved.getUserId()).isEqualTo(userId);
		assertThat(saved.getSlotId()).isEqualTo(slotId);
		assertThat(saved.getVisitAt()).isEqualTo(LocalDateTime.of(date, slotTime));
		assertThat(saved.getPartySize()).isEqualTo(partySize);
		assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

		ArgumentCaptor<DailySlotCapacity> captor = ArgumentCaptor.forClass(DailySlotCapacity.class);
		verify(dailySlotCapacityRepository, times(1)).updateRemainingCount(captor.capture());

		DailySlotCapacity updated = captor.getValue();
		assertThat(updated.getCapacityId()).isEqualTo(777L);
		assertThat(updated.getSlotId()).isEqualTo(slotId);
		assertThat(updated.getDate()).isEqualTo(date);
		assertThat(updated.getRemainingCount()).isEqualTo(8);	}

	@Test
	@DisplayName("실패 - 유저 없음")
	void create_fail_userNotFound() {
		// given
		String email = "customer01@test.com";
		ReservationRequestDto req = new ReservationRequestDto(1L, LocalDate.of(2026, 1, 26), 2, "");

		given(userRepository.fetchByEmail(email))
			.willThrow(new UserException(ErrorCode.RESOURCE_NOT_FOUND, "User"));

		// when & then
		assertThatThrownBy(() -> reservationService.create(email, req))
			.isInstanceOf(UserException.class);

		verifyNoInteractions(restaurantSlotRepository, dailySlotCapacityRepository, reservationRepository);
	}

	@Test
	@DisplayName("실패 - 중복 예약 예외 발생")
	void create_fail_duplicatedTime_preCheck() {
		// given
		String email = "customer01@test.com";
		Long userId = 1L;
		Long slotId = 10L;
		Long restaurantId = 100L;
		LocalDate date = LocalDate.of(2026, 1, 26);
		LocalTime slotTime = LocalTime.of(19, 0);

		User user = createTestUser(userId, email);
		RestaurantSlot slot = createTestSlot(slotId, restaurantId, slotTime);

		given(userRepository.fetchByEmail(email)).willReturn(user);
		given(restaurantSlotRepository.fetchById(slotId)).willReturn(slot);
		given(reservationRepository.existsByUserIdAndVisitAtAndStatus(
			userId, LocalDateTime.of(date, slotTime), ReservationStatus.CONFIRMED
		)).willReturn(true);

		// when & then
		assertThatThrownBy(() -> reservationService.create(email, new ReservationRequestDto(slotId, date, 2, "")))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
				.isEqualTo(ErrorCode.RESERVATION_DUPLICATED_TIME));

		verifyNoInteractions(dailySlotCapacityRepository);
		verify(reservationRepository, never()).save(any());
	}

	@Test
	@DisplayName("실패 - capacity row 없음(해당 날짜 슬롯 미오픈)")
	void create_fail_slotNotOpened() {
		// given
		String email = "customer01@test.com";
		Long userId = 1L;
		Long slotId = 10L;
		Long restaurantId = 100L;
		LocalDate date = LocalDate.of(2026, 1, 26);
		LocalTime slotTime = LocalTime.of(19, 0);

		User user = createTestUser(userId, email);
		RestaurantSlot slot = createTestSlot(slotId, restaurantId, slotTime);

		given(userRepository.fetchByEmail(email)).willReturn(user);
		given(restaurantSlotRepository.fetchById(slotId)).willReturn(slot);
		given(reservationRepository.existsByUserIdAndVisitAtAndStatus(
			eq(userId), any(LocalDateTime.class), eq(ReservationStatus.CONFIRMED)
		)).willReturn(false);
		given(dailySlotCapacityRepository.findBySlotIdAndDate(slotId, date)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> reservationService.create(email, new ReservationRequestDto(slotId, date, 2, "")))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
				.isEqualTo(ErrorCode.RESERVATION_SLOT_NOT_OPENED));

		verify(reservationRepository, never()).save(any());
		verify(dailySlotCapacityRepository, never()).updateRemainingCount(any(DailySlotCapacity.class));
	}

	@Test
	@DisplayName("실패 - 좌석 부족")
	void create_fail_capacityNotEnough() {
		// given
		String email = "customer01@test.com";
		Long userId = 1L;
		Long slotId = 10L;
		Long restaurantId = 100L;
		LocalDate date = LocalDate.of(2026, 1, 26);
		LocalTime slotTime = LocalTime.of(19, 0);

		User user = createTestUser(userId, email);
		RestaurantSlot slot = createTestSlot(slotId, restaurantId, slotTime);

		DailySlotCapacity capacity = DailySlotCapacity.builder()
			.capacityId(777L)
			.slotId(slotId)
			.date(date)
			.remainingCount(1)
			.version(0L)
			.build();

		given(userRepository.fetchByEmail(email)).willReturn(user);
		given(restaurantSlotRepository.fetchById(slotId)).willReturn(slot);
		given(reservationRepository.existsByUserIdAndVisitAtAndStatus(
			eq(userId), any(LocalDateTime.class), eq(ReservationStatus.CONFIRMED)
		)).willReturn(false);
		given(dailySlotCapacityRepository.findBySlotIdAndDate(slotId, date)).willReturn(Optional.of(capacity));

		// when & then
		assertThatThrownBy(() -> reservationService.create(email, new ReservationRequestDto(slotId, date, 2, "")))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
				.isEqualTo(ErrorCode.RESERVATION_CAPACITY_NOT_ENOUGH));

		verify(reservationRepository, never()).save(any());
		verify(dailySlotCapacityRepository, never()).updateRemainingCount(any(DailySlotCapacity.class));
	}

	@Test
	@DisplayName("실패 - 슬롯 없음")
	void create_fail_slotNotFound() {
		// given
		String email = "customer01@test.com";
		Long userId = 1L;
		Long slotId = 10L;
		LocalDate date = LocalDate.of(2026, 1, 26);

		User user = createTestUser(userId, email);

		given(userRepository.fetchByEmail(email)).willReturn(user);
		given(restaurantSlotRepository.fetchById(slotId))
			.willThrow(new RestaurantException(ErrorCode.RESOURCE_NOT_FOUND, "RestaurantSlot"));

		// when & then
		assertThatThrownBy(() -> reservationService.create(email, new ReservationRequestDto(slotId, date, 2, "")))
			.isInstanceOf(RestaurantException.class);

		verifyNoInteractions(dailySlotCapacityRepository, reservationRepository);
	}

	@Test
	@DisplayName("예약 취소 성공 - CANCELED 저장 + capacity 복구")
	void cancel_success() {
		// given
		String email = "customer01@test.com";
		Long userId = 1L;
		Long reservationId = 999L;
		Long slotId = 10L;

		LocalDateTime visitAt = LocalDateTime.now().plusDays(2);
		LocalDate date = visitAt.toLocalDate();

		int partySize = 2;

		User user = createTestUser(userId, email);

		Reservation reservation = Reservation.builder()
			.reservationId(reservationId)
			.userId(userId)
			.slotId(slotId)
			.visitAt(visitAt)
			.partySize(partySize)
			.note("note")
			.status(ReservationStatus.CONFIRMED)
			.build();

		DailySlotCapacity capacity = DailySlotCapacity.builder()
			.capacityId(777L)
			.slotId(slotId)
			.date(date)
			.remainingCount(8) // 현재 남은 수량
			.version(0L)
			.build();

		given(userRepository.fetchByEmail(email)).willReturn(user);
		given(reservationRepository.fetchById(reservationId)).willReturn(reservation);
		given(dailySlotCapacityRepository.findBySlotIdAndDate(slotId, date)).willReturn(Optional.of(capacity));
		given(reservationRepository.save(any(Reservation.class))).willAnswer(inv -> inv.getArgument(0));

		// when
		Reservation canceled = reservationService.cancel(email, reservationId);

		// then
		assertThat(canceled.getReservationId()).isEqualTo(reservationId);
		assertThat(canceled.getStatus()).isEqualTo(ReservationStatus.CANCELED);

		ArgumentCaptor<DailySlotCapacity> captor = ArgumentCaptor.forClass(DailySlotCapacity.class);
		verify(dailySlotCapacityRepository, times(1)).updateRemainingCount(captor.capture());

		DailySlotCapacity updated = captor.getValue();
		assertThat(updated.getCapacityId()).isEqualTo(777L);
		assertThat(updated.getSlotId()).isEqualTo(slotId);
		assertThat(updated.getDate()).isEqualTo(date);
		assertThat(updated.getRemainingCount()).isEqualTo(10); // 8 + 2 복구

		verify(reservationRepository, times(1)).save(any(Reservation.class));
	}

	@Test
	@DisplayName("예약 취소 실패 - 본인 예약 아님(403)")
	void cancel_fail_forbidden() {
		// given
		String email = "customer01@test.com";
		Long userId = 1L;
		Long otherUserId = 2L;
		Long reservationId = 999L;
		Long slotId = 10L;

		LocalDateTime visitAt = LocalDateTime.now().plusDays(2);

		User user = createTestUser(userId, email);

		Reservation reservation = Reservation.builder()
			.reservationId(reservationId)
			.userId(otherUserId) // 다른 유저 예약
			.slotId(slotId)
			.visitAt(visitAt)
			.partySize(2)
			.status(ReservationStatus.CONFIRMED)
			.build();

		given(userRepository.fetchByEmail(email)).willReturn(user);
		given(reservationRepository.fetchById(reservationId)).willReturn(reservation);

		// when & then
		assertThatThrownBy(() -> reservationService.cancel(email, reservationId))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
				.isEqualTo(ErrorCode.RESERVATION_FORBIDDEN));

		verifyNoInteractions(dailySlotCapacityRepository);
		verify(reservationRepository, never()).save(any());
	}

	@Test
	@DisplayName("예약 취소 실패 - 이미 취소된 예약(409)")
	void cancel_fail_alreadyCanceled() {
		// given
		String email = "customer01@test.com";
		Long userId = 1L;
		Long reservationId = 999L;
		Long slotId = 10L;

		LocalDateTime visitAt = LocalDateTime.now().plusDays(2);

		User user = createTestUser(userId, email);

		Reservation reservation = Reservation.builder()
			.reservationId(reservationId)
			.userId(userId)
			.slotId(slotId)
			.visitAt(visitAt)
			.partySize(2)
			.status(ReservationStatus.CANCELED) // 이미 취소
			.build();

		given(userRepository.fetchByEmail(email)).willReturn(user);
		given(reservationRepository.fetchById(reservationId)).willReturn(reservation);

		// when & then
		assertThatThrownBy(() -> reservationService.cancel(email, reservationId))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
				.isEqualTo(ErrorCode.RESERVATION_ALREADY_CANCELED));

		verifyNoInteractions(dailySlotCapacityRepository);
		verify(reservationRepository, never()).save(any());
	}

	@Test
	@DisplayName("예약 취소 실패 - 취소 가능 기한(24시간) 지남")
	void cancel_fail_deadlinePassed() {
		// given
		String email = "customer01@test.com";
		Long userId = 1L;
		Long reservationId = 999L;
		Long slotId = 10L;

		// visitAt이 현재로부터 24시간 이내면 취소 불가
		LocalDateTime visitAt = LocalDateTime.now().plusHours(23);

		User user = createTestUser(userId, email);

		Reservation reservation = Reservation.builder()
			.reservationId(reservationId)
			.userId(userId)
			.slotId(slotId)
			.visitAt(visitAt)
			.partySize(2)
			.status(ReservationStatus.CONFIRMED)
			.build();

		given(userRepository.fetchByEmail(email)).willReturn(user);
		given(reservationRepository.fetchById(reservationId)).willReturn(reservation);

		// when & then
		assertThatThrownBy(() -> reservationService.cancel(email, reservationId))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> assertThat(((ReservationException) ex).getErrorCode())
				.isEqualTo(ErrorCode.RESERVATION_CANCEL_DEADLINE_PASSED));

		verifyNoInteractions(dailySlotCapacityRepository);
		verify(reservationRepository, never()).save(any());
	}

	private User createTestUser(Long userId, String email) {
		return User.builder()
			.userId(userId)
			.email(email)
			.name("유저")
			.phone("010-0000-0000")
			.password("encrypted-password")
			.userRole(UserRole.CUSTOMER)
			.build();
	}

	private RestaurantSlot createTestSlot(Long slotId, Long restaurantId, LocalTime time) {
		return RestaurantSlot.builder()
			.slotId(slotId)
			.restaurantId(restaurantId)
			.time(time)
			.maxCapacity(10)
			.build();
	}
}
