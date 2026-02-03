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
import com.reservation.tablereservationservice.fixture.ReservationFixture;
import com.reservation.tablereservationservice.fixture.RestaurantFixture;
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

	private static final LocalDate BASE_DATE = LocalDate.of(2030, 1, 1);
	private static final LocalTime BASE_TIME = LocalTime.of(19, 0);
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
		customer = UserFixture
			.customer()
			.userId(1L)
			.build();

		owner = UserFixture.owner()
			.userId(2L)
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

		ReservationRequestDto req = createReservationRequest(restaurantSlot.getSlotId(), date, partySize, "note");

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

		// then - save argument 검증
		ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
		verify(reservationRepository).save(reservationCaptor.capture());

		Reservation captured = reservationCaptor.getValue();
		assertThat(captured.getUserId()).isEqualTo(customer.getUserId());
		assertThat(captured.getSlotId()).isEqualTo(restaurantSlot.getSlotId());
		assertThat(captured.getVisitAt()).isEqualTo(visitAt);
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
		ReservationRequestDto req = createReservationRequest(restaurantSlot.getSlotId(), BASE_DATE, 2, "");

		given(userRepository.fetchByEmail(customer.getEmail()))
			.willThrow(new UserException(ErrorCode.RESOURCE_NOT_FOUND, "User"));

		// when & then
		assertThatThrownBy(() -> reservationService.create(customer.getEmail(), req))
			.isInstanceOf(UserException.class);

		verifyNoInteractions(restaurantSlotRepository, dailySlotCapacityRepository, reservationRepository);
	}

	@Test
	@DisplayName("예약 요청 실패 - 중복 예약 예외 발생")
	void create_fail_duplicatedTime_preCheck() {
		// given
		LocalDate date = BASE_DATE;
		LocalDateTime visitAt = LocalDateTime.of(date, restaurantSlot.getTime());

		ReservationRequestDto req = createReservationRequest(restaurantSlot.getSlotId(), date, 2, "");

		given(userRepository.fetchByEmail(customer.getEmail())).willReturn(customer);
		given(restaurantSlotRepository.fetchById(restaurantSlot.getSlotId())).willReturn(restaurantSlot);

		given(reservationRepository.existsByUserIdAndVisitAtAndStatus(
			customer.getUserId(), visitAt, ReservationStatus.CONFIRMED
		)).willReturn(true);

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
		LocalDate date = BASE_DATE;
		LocalDateTime visitAt = LocalDateTime.of(date, restaurantSlot.getTime());

		ReservationRequestDto req = createReservationRequest(restaurantSlot.getSlotId(), date, 2, "");

		given(userRepository.fetchByEmail(customer.getEmail())).willReturn(customer);
		given(restaurantSlotRepository.fetchById(restaurantSlot.getSlotId())).willReturn(restaurantSlot);

		given(reservationRepository.existsByUserIdAndVisitAtAndStatus(
			customer.getUserId(), visitAt, ReservationStatus.CONFIRMED
		)).willReturn(false);

		given(dailySlotCapacityRepository.findBySlotIdAndDate(restaurantSlot.getSlotId(), date))
			.willReturn(Optional.empty());

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
		LocalDate date = BASE_DATE;
		LocalDateTime visitAt = LocalDateTime.of(date, restaurantSlot.getTime());

		DailySlotCapacity capacity = DailySlotCapacityFixture.capacity()
			.slotId(restaurantSlot.getSlotId())
			.date(date)
			.remainingCount(1)
			.build();

		ReservationRequestDto req = createReservationRequest(restaurantSlot.getSlotId(), date, 2, "");

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
	@DisplayName("사용자 예약 목록 조회 성공 - 검색 조건이 repository 파라미터로 전달된다.")
	void findMyReservations_success() {
		// given
		LocalDate fromDate = BASE_DATE.minusDays(1);
		LocalDate toDate = BASE_DATE.plusDays(7);
		ReservationStatus status = ReservationStatus.CONFIRMED;

		ReservationSearchDto searchDto = createSearchDto(fromDate, toDate, status);

		Reservation reservation = ReservationFixture.confirmed()
			.userId(customer.getUserId())
			.slotId(restaurantSlot.getSlotId())
			.visitAt(LocalDateTime.of(BASE_DATE, restaurantSlot.getTime()))
			.build();

		Page<Reservation> page = new PageImpl<>(List.of(reservation), PAGEABLE, 1);

		Restaurant restaurant = RestaurantFixture.restaurant()
			.restaurantId(restaurantSlot.getRestaurantId())
			.name("강남 한상")
			.build();

		given(userRepository.fetchByEmail(customer.getEmail())).willReturn(customer);
		given(reservationRepository.findMyReservations(
			eq(customer.getUserId()),
			eq(status),
			eq(fromDate.atStartOfDay()),
			eq(toDate.atTime(LocalTime.MAX)),
			eq(PAGEABLE)
		)).willReturn(page);

		given(restaurantSlotRepository.findAllById(anyList()))
			.willReturn(List.of(restaurantSlot));

		given(restaurantRepository.findAllById(anyList()))
			.willReturn(List.of(restaurant));

		// when
		PageResponseDto<ReservationListResponseDto> result =
			reservationService.findMyReservations(customer.getEmail(), searchDto);

		// then
		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().getFirst().getRestaurantName()).isEqualTo("강남 한상");
		assertThat(result.getContent().getFirst().getStatus()).isEqualTo(status);
	}

	@Test
	@DisplayName("점주 예약 목록 조회 성공 - 검색 조건이 repository 파라미터로 전달된다.")
	void findOwnerReservations_success() {
		// given
		LocalDate fromDate = BASE_DATE.minusDays(1);
		LocalDate toDate = BASE_DATE.plusDays(7);
		ReservationStatus status = ReservationStatus.CONFIRMED;

		ReservationSearchDto searchDto = createSearchDto(fromDate, toDate, status);

		Restaurant ownerRestaurant = RestaurantFixture.restaurant()
			.restaurantId(200L)
			.ownerId(owner.getUserId())
			.build();

		RestaurantSlot ownerSlot = RestaurantSlotFixture.slot()
			.slotId(300L)
			.restaurantId(ownerRestaurant.getRestaurantId())
			.time(BASE_TIME)
			.build();

		Reservation reservation = ReservationFixture.confirmed()
			.userId(customer.getUserId())
			.slotId(ownerSlot.getSlotId())
			.visitAt(LocalDateTime.of(BASE_DATE, ownerSlot.getTime()))
			.build();

		Page<Reservation> page = new PageImpl<>(List.of(reservation), PAGEABLE, 1);

		given(userRepository.fetchByEmail(owner.getEmail())).willReturn(owner);

		given(restaurantRepository.findAllByOwnerId(owner.getUserId()))
			.willReturn(List.of(ownerRestaurant));

		given(reservationRepository.findOwnerReservations(
			eq(List.of(ownerRestaurant.getRestaurantId())),
			eq(status),
			eq(fromDate.atStartOfDay()),
			eq(toDate.atTime(LocalTime.MAX)),
			eq(PAGEABLE)
		)).willReturn(page);

		given(restaurantSlotRepository.findAllById(anyList()))
			.willReturn(List.of(ownerSlot));

		given(restaurantRepository.findAllById(anyList()))
			.willReturn(List.of(ownerRestaurant));

		given(userRepository.findAllById(anyList()))
			.willReturn(List.of(customer));

		// when
		PageResponseDto<ReservationListResponseDto> result =
			reservationService.findOwnerReservations(owner.getEmail(), searchDto);

		// then
		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().getFirst().getRestaurantName()).isEqualTo(ownerRestaurant.getName());
		assertThat(result.getContent().getFirst().getStatus()).isEqualTo(status);

		verify(restaurantSlotRepository).findAllById(argThat(ids -> ids.contains(300L)));
		verify(restaurantRepository).findAllById(argThat(ids -> ids.contains(200L)));
	}

	@Test
	@DisplayName("예약 취소 성공 - CANCELED 저장 + capacity 복구")
	void cancel_success() {
		// given
		Long reservationId = 999L;
		int partySize = 2;

		LocalDateTime now = LocalDateTime.now();
		LocalDateTime visitAt = now.plusDays(2);
		LocalDate date = visitAt.toLocalDate();

		Reservation reservation = ReservationFixture.confirmed()
			.reservationId(reservationId)
			.userId(customer.getUserId())
			.slotId(restaurantSlot.getSlotId())
			.visitAt(visitAt)
			.partySize(partySize)
			.build();

		DailySlotCapacity capacity = DailySlotCapacityFixture.capacity()
			.capacityId(777L)
			.slotId(reservation.getSlotId())
			.date(date)
			.remainingCount(8)
			.build();

		given(userRepository.fetchByEmail(customer.getEmail())).willReturn(customer);
		given(reservationRepository.fetchById(reservationId)).willReturn(reservation);
		given(dailySlotCapacityRepository.findBySlotIdAndDate(reservation.getSlotId(), date))
			.willReturn(Optional.of(capacity));

		// when
		Reservation canceled = reservationService.cancel(customer.getEmail(), reservationId);

		// then
		assertThat(canceled.getReservationId()).isEqualTo(reservationId);
		assertThat(canceled.getStatus()).isEqualTo(ReservationStatus.CANCELED);

		ArgumentCaptor<DailySlotCapacity> captor = ArgumentCaptor.forClass(DailySlotCapacity.class);
		verify(dailySlotCapacityRepository, times(1)).updateRemainingCount(captor.capture());

		DailySlotCapacity updated = captor.getValue();
		assertThat(updated.getCapacityId()).isEqualTo(777L);
		assertThat(updated.getSlotId()).isEqualTo(reservation.getSlotId());
		assertThat(updated.getDate()).isEqualTo(date);
		assertThat(updated.getRemainingCount()).isEqualTo(10); // 8 + 2 복구

		verify(reservationRepository, times(1)).updateStatus(reservation);
	}

	@Test
	@DisplayName("예약 취소 실패 - 본인 예약 아님(403)")
	void cancel_fail_forbidden() {
		// given
		Long reservationId = 999L;
		LocalDateTime now = LocalDateTime.now();
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
	@DisplayName("예약 취소 실패 - 이미 취소된 예약(409)")
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
	@DisplayName("예약 취소 실패 - 취소 가능 기한(24시간) 지남")
	void cancel_fail_deadlinePassed() {
		// given
		Long reservationId = 999L;

		// visitAt이 현재로부터 24시간 이내면 취소 불가
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

	private ReservationSearchDto createSearchDto(
		LocalDate fromDate,
		LocalDate toDate,
		ReservationStatus status
	) {
		ReservationSearchDto searchDto = new ReservationSearchDto();
		searchDto.setFromDate(fromDate);
		searchDto.setToDate(toDate);
		searchDto.setStatus(status);
		searchDto.setPageable(ReservationServiceTest.PAGEABLE);
		return searchDto;
	}

	private ReservationRequestDto createReservationRequest(Long slotId, LocalDate date, int partySize, String note) {
		return new ReservationRequestDto(slotId, date, partySize, note);
	}
}
