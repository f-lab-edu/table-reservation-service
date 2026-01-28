package com.reservation.tablereservationservice.application.reservation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import com.reservation.tablereservationservice.domain.restaurant.RestaurantRepository;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRepository;
import com.reservation.tablereservationservice.domain.user.UserRole;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.global.exception.RestaurantException;
import com.reservation.tablereservationservice.global.exception.UserException;
import com.reservation.tablereservationservice.presentation.common.PageResponseDto;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationListResponseDto;
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

	@Mock
	private RestaurantRepository restaurantRepository;

	@InjectMocks
	private ReservationService reservationService;

	@Test
	@DisplayName("예약 요청 성공 - CONFIRMED 저장 + capacity 차감")
	void create_success() {
		// given
		String email = "customer01@test.com";
		LocalDate date = LocalDate.of(2026, 1, 26);
		LocalTime slotTime = LocalTime.of(19, 0);
		Long userId = 1L;
		Long slotId = 10L;
		Long restaurantId = 100L;
		int partySize = 2;

		User user = createTestUser(userId, email, UserRole.CUSTOMER);
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
		given(reservationRepository.existsByUserIdAndVisitAtAndStatus(
			userId, LocalDateTime.of(date, slotTime),
			ReservationStatus.CONFIRMED
		)).willReturn(false);
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

		verify(dailySlotCapacityRepository, times(1)).updateRemainingCount(eq(777L), eq(8));
	}

	@Test
	@DisplayName("예약 요청 실패 - 유저 없음")
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
	@DisplayName("예약 요청 실패 - 중복 예약 예외 발생")
	void create_fail_duplicatedTime_preCheck() {
		// given
		String email = "customer01@test.com";
		Long userId = 1L;
		Long slotId = 10L;
		Long restaurantId = 100L;
		LocalDate date = LocalDate.of(2026, 1, 26);
		LocalTime slotTime = LocalTime.of(19, 0);

		User user = createTestUser(userId, email, UserRole.CUSTOMER);
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
	@DisplayName("예약 요청 실패 - capacity row 없음(해당 날짜 슬롯 미오픈)")
	void create_fail_slotNotOpened() {
		// given
		String email = "customer01@test.com";
		Long userId = 1L;
		Long slotId = 10L;
		Long restaurantId = 100L;
		LocalDate date = LocalDate.of(2026, 1, 26);
		LocalTime slotTime = LocalTime.of(19, 0);

		User user = createTestUser(userId, email, UserRole.CUSTOMER);
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
		verify(dailySlotCapacityRepository, never()).updateRemainingCount(anyLong(), anyInt());
	}

	@Test
	@DisplayName("예약 요청 실패 - 좌석 부족")
	void create_fail_capacityNotEnough() {
		// given
		String email = "customer01@test.com";
		Long userId = 1L;
		Long slotId = 10L;
		Long restaurantId = 100L;
		LocalDate date = LocalDate.of(2026, 1, 26);
		LocalTime slotTime = LocalTime.of(19, 0);

		User user = createTestUser(userId, email, UserRole.CUSTOMER);
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
		verify(dailySlotCapacityRepository, never()).updateRemainingCount(anyLong(), anyInt());
	}

	@Test
	@DisplayName("예약 요청 실패 - 슬롯 없음")
	void create_fail_slotNotFound() {
		// given
		String email = "customer01@test.com";
		Long userId = 1L;
		Long slotId = 10L;
		LocalDate date = LocalDate.of(2026, 1, 26);

		User user = createTestUser(userId, email, UserRole.CUSTOMER);

		given(userRepository.fetchByEmail(email)).willReturn(user);
		given(restaurantSlotRepository.fetchById(slotId))
			.willThrow(new RestaurantException(ErrorCode.RESOURCE_NOT_FOUND, "RestaurantSlot"));

		// when & then
		assertThatThrownBy(() -> reservationService.create(email, new ReservationRequestDto(slotId, date, 2, "")))
			.isInstanceOf(RestaurantException.class);

		verifyNoInteractions(dailySlotCapacityRepository, reservationRepository);
	}

	@Test
	@DisplayName("사용자 예약 목록 조회 성공 - 기본 기간(오늘~한달) 적용 + repo 호출 검증")
	void findMyReservations_success() {
		// given
		String email = "customer01@test.com";
		Long userId = 1L;
		Pageable pageable = PageRequest.of(0, 10);

		User testUser = createTestUser(userId, email, UserRole.CUSTOMER);

		LocalDate today = LocalDate.now();
		LocalDate expectedEnd = today.plusMonths(1);

		List<ReservationListResponseDto> content = List.of(
			ReservationListResponseDto.builder()
				.userName("유저")
				.userPhone("010-1111-2222")
				.reservationId(10L)
				.restaurantId(100L)
				.restaurantName("맛집A")
				.visitAt(LocalDateTime.of(today, LocalTime.of(19, 0)))
				.partySize(2)
				.status(ReservationStatus.CONFIRMED)
				.build()
		);

		Page<ReservationListResponseDto> page = new PageImpl<>(content, pageable, 1);

		given(userRepository.fetchByEmail(email)).willReturn(testUser);
		given(reservationRepository.findMyReservations(
			eq(userId),
			isNull(),
			any(LocalDateTime.class),
			any(LocalDateTime.class),
			eq(pageable)
		)).willReturn(page);

		// when
		PageResponseDto<ReservationListResponseDto> result =
			reservationService.findMyReservations(email, null, null, null, pageable);

		// then
		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getTotalElements()).isEqualTo(1);
		assertThat(result.getContent().get(0).getRestaurantName()).isEqualTo("맛집A");

		verify(userRepository, times(1)).fetchByEmail(email);
		verify(reservationRepository, times(1)).findMyReservations(
			eq(userId),
			isNull(),
			eq(today.atStartOfDay()),
			eq(expectedEnd.atTime(LocalTime.MAX)),
			eq(pageable)
		);
	}

	@Test
	@DisplayName("점주 예약 목록 조회 성공 - restaurantIds 조회 후 ownerReservations 조회")
	void findOwnerReservations_success() {
		// given
		String email = "owner01@test.com";
		Long ownerId = 2L;
		Pageable pageable = PageRequest.of(0, 10);

		User owner = createTestUser(ownerId, email, UserRole.OWNER);

		LocalDate fromDate = LocalDate.of(2026, 1, 1);
		LocalDate toDate = LocalDate.of(2026, 1, 31);

		List<Long> restaurantIds = List.of(100L, 200L);

		List<ReservationListResponseDto> content = List.of(
			ReservationListResponseDto.builder()
				.userName("유저")
				.userPhone("010-1111-2222")
				.reservationId(10L)
				.restaurantId(100L)
				.restaurantName("맛집A")
				.visitAt(LocalDateTime.of(2026, 1, 10, 19, 0))
				.partySize(2)
				.status(ReservationStatus.CONFIRMED)
				.build()
		);
		Page<ReservationListResponseDto> page = new PageImpl<>(content, pageable, 1);

		given(userRepository.fetchByEmail(email)).willReturn(owner);
		given(restaurantRepository.findRestaurantIdsByOwnerId(ownerId)).willReturn(restaurantIds);
		given(reservationRepository.findOwnerReservations(
			eq(restaurantIds),
			eq(ReservationStatus.CONFIRMED),
			any(LocalDateTime.class),
			any(LocalDateTime.class),
			eq(pageable)
		)).willReturn(page);

		// when
		PageResponseDto<ReservationListResponseDto> result =
			reservationService.findOwnerReservations(email, fromDate, toDate, ReservationStatus.CONFIRMED, pageable);

		// then
		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).getUserName()).isEqualTo("유저");
		assertThat(result.getContent().get(0).getUserPhone()).isEqualTo("010-1111-2222");

		verify(userRepository, times(1)).fetchByEmail(email);
		verify(restaurantRepository, times(1)).findRestaurantIdsByOwnerId(ownerId);
		verify(reservationRepository, times(1)).findOwnerReservations(
			eq(restaurantIds),
			eq(ReservationStatus.CONFIRMED),
			eq(fromDate.atStartOfDay()),
			eq(toDate.atTime(LocalTime.MAX)),
			eq(pageable)
		);
	}

	private User createTestUser(Long userId, String email, UserRole userRole) {
		return User.builder()
			.userId(userId)
			.email(email)
			.name("유저")
			.phone("010-0000-0000")
			.password("encrypted-password")
			.userRole(userRole)
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
