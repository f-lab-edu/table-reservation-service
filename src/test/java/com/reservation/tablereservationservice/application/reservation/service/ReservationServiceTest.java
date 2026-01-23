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
			.maxCount(10)
			.version(0L)
			.build();

		ReservationRequestDto req = new ReservationRequestDto(slotId, date, partySize, "note");

		given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
		given(restaurantSlotRepository.findById(slotId)).willReturn(Optional.of(slot));
		given(reservationRepository.existsByUserIdAndVisitAtAndStatus(userId, LocalDateTime.of(date, slotTime),
			ReservationStatus.CONFIRMED)).willReturn(false);
		given(dailySlotCapacityRepository.findBySlotIdAndDate(slotId, date)).willReturn(Optional.of(capacity));
		given(reservationRepository.save(any(Reservation.class))).willAnswer(inv -> {
			Reservation r = inv.getArgument(0);
			return Reservation.builder()
				.reservationId(999L)
				.userId(r.getUserId())
				.slotId(r.getSlotId())
				.date(r.getDate())
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

		// capacity update 호출 + 차감 값 검증
		ArgumentCaptor<DailySlotCapacity> capacityCaptor = ArgumentCaptor.forClass(DailySlotCapacity.class);
		verify(dailySlotCapacityRepository, times(1)).update(capacityCaptor.capture());
		assertThat(capacityCaptor.getValue().getRemainingCount()).isEqualTo(8);

	}

	@Test
	@DisplayName("실패 - 유저 없음")
	void create_fail_userNotFound() {
		// given
		String email = "customer01@test.com";
		ReservationRequestDto req = new ReservationRequestDto(1L, LocalDate.of(2026, 1, 26), 2, "");

		given(userRepository.findByEmail(email)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> reservationService.create(email, req))
			.isInstanceOf(UserException.class);
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

		given(userRepository.findByEmail(email)).willReturn(Optional.of(createTestUser(userId, email)));
		given(restaurantSlotRepository.findById(slotId)).willReturn(
			Optional.of(createTestSlot(slotId, restaurantId, slotTime)));
		given(reservationRepository.existsByUserIdAndVisitAtAndStatus(userId, LocalDateTime.of(date, slotTime),
			ReservationStatus.CONFIRMED)).willReturn(true);

		// when & then
		assertThatThrownBy(() -> reservationService.create(email, new ReservationRequestDto(slotId, date, 2, "")))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> {
				ReservationException re = (ReservationException)ex;
				assertThat(re.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_DUPLICATED_TIME);
			});
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

		given(userRepository.findByEmail(email)).willReturn(Optional.of(createTestUser(userId, email)));
		given(restaurantSlotRepository.findById(slotId)).willReturn(
			Optional.of(createTestSlot(slotId, restaurantId, slotTime)));
		given(reservationRepository.existsByUserIdAndVisitAtAndStatus(eq(userId), any(LocalDateTime.class),
			eq(ReservationStatus.CONFIRMED))).willReturn(false);
		given(dailySlotCapacityRepository.findBySlotIdAndDate(slotId, date)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> reservationService.create(email, new ReservationRequestDto(slotId, date, 2, "")))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> {
				ReservationException re = (ReservationException)ex;
				assertThat(re.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_SLOT_NOT_OPENED);
			});
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

		DailySlotCapacity capacity = DailySlotCapacity.builder()
			.capacityId(777L)
			.slotId(slotId)
			.date(date)
			.remainingCount(1)
			.maxCount(10)
			.version(0L)
			.build();

		given(userRepository.findByEmail(email)).willReturn(Optional.of(createTestUser(userId, email)));
		given(restaurantSlotRepository.findById(slotId)).willReturn(
			Optional.of(createTestSlot(slotId, restaurantId, slotTime)));
		given(reservationRepository.existsByUserIdAndVisitAtAndStatus(eq(userId), any(LocalDateTime.class),
			eq(ReservationStatus.CONFIRMED))).willReturn(false);
		given(dailySlotCapacityRepository.findBySlotIdAndDate(slotId, date)).willReturn(Optional.of(capacity));

		// when & then
		assertThatThrownBy(() -> reservationService.create(email, new ReservationRequestDto(slotId, date, 2, "")))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> {
				ReservationException re = (ReservationException)ex;
				assertThat(re.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_CAPACITY_NOT_ENOUGH);
			});
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
