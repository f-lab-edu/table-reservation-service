package com.reservation.tablereservationservice.application.reservation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacity;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantRepository;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRepository;
import com.reservation.tablereservationservice.fixture.DailySlotCapacityFixture;
import com.reservation.tablereservationservice.fixture.RestaurantFixture;
import com.reservation.tablereservationservice.fixture.RestaurantSlotFixture;
import com.reservation.tablereservationservice.fixture.UserFixture;
import com.reservation.tablereservationservice.infrastructure.mq.ReservationMqPublisher;
import com.reservation.tablereservationservice.infrastructure.mq.ReservationRequestMessage;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReservationMessagingTest {

	private static final LocalDate BASE_DATE = LocalDate.of(2030, 1, 1);
	private static final LocalTime BASE_TIME = LocalTime.of(19, 0);

	@MockitoBean
	private ReservationMqPublisher reservationMqPublisher;

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
	private RestaurantSlot restaurantSlot;

	@BeforeEach
	void setUp() {
		customer = userRepository.save(UserFixture.customer().build());
		var owner = userRepository.save(UserFixture.owner().build());

		var restaurant = restaurantRepository.save(
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
	@DisplayName("예약 요청 발행 - submitReservation 호출 시 올바른 메시지가 MQ에 발행된다")
	void submitReservation_success_publishesCorrectMessage() {
		// given
		LocalDate date = BASE_DATE;
		int partySize = 2;

		ReservationRequestDto req = createReservationRequest(restaurantSlot.getSlotId(), date, partySize, "note");

		// when
		reservationService.submitReservation(customer.getEmail(), req);

		// then
		ArgumentCaptor<ReservationRequestMessage> captor = ArgumentCaptor.forClass(ReservationRequestMessage.class);
		verify(reservationMqPublisher).publish(captor.capture());

		ReservationRequestMessage published = captor.getValue();
		assertThat(published.userEmail()).isEqualTo(customer.getEmail());
		assertThat(published.slotId()).isEqualTo(restaurantSlot.getSlotId());
		assertThat(published.date()).isEqualTo(date);
		assertThat(published.partySize()).isEqualTo(partySize);
	}

	@Test
	@DisplayName("예약 요청 처리 - handleReservationRequest 호출 시 예약이 생성되고 잔여석이 차감된다")
	void handleReservationRequest_success_createsReservationAndDecreasesCapacity() {
		// given
		LocalDate date = BASE_DATE;
		int partySize = 2;
		long processingOrder = 42L;

		saveCapacity(restaurantSlot.getSlotId(), date, 10);

		ReservationRequestDto dto = createReservationRequest(restaurantSlot.getSlotId(), date, partySize, "note");
		ReservationRequestMessage message = ReservationRequestMessage.from(customer.getEmail(), dto);

		// when
		reservationService.handleReservationRequest(message, processingOrder);

		// then
		assertThat(reservationRepository.existsByUserIdAndVisitAtAndStatus(
			customer.getUserId(),
			date.atTime(restaurantSlot.getTime()),
			ReservationStatus.CONFIRMED
		)).isTrue();

		DailySlotCapacity after = dailySlotCapacityRepository.findBySlotIdAndDate(restaurantSlot.getSlotId(), date)
			.orElseThrow(() -> new IllegalStateException("DailySlotCapacity not found"));
		assertThat(after.getRemainingCount()).isEqualTo(8);
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

	private ReservationRequestDto createReservationRequest(Long slotId, LocalDate date, int partySize, String note) {
		return new ReservationRequestDto(slotId, date, partySize, note);
	}
}
