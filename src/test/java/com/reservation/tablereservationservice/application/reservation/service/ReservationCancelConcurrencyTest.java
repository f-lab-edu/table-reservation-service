package com.reservation.tablereservationservice.application.reservation.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.reservation.tablereservationservice.application.reservation.facade.ReservationOptimisticFacade;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
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
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;

@SpringBootTest
@ActiveProfiles("test")
class ReservationCancelConcurrencyTest {

	private static final LocalDate VISIT_DATE = LocalDate.of(2030, 6, 1);
	private static final LocalTime SLOT_TIME = LocalTime.of(19, 0);

	@Autowired
	private ReservationOptimisticFacade cancelFacade;

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
	private Reservation reservation;

	@BeforeEach
	void setUp() {
		customer = userRepository.save(UserFixture.customer().build());
		User owner = userRepository.save(UserFixture.owner().build());

		Restaurant restaurant = restaurantRepository.save(
			RestaurantFixture.restaurant()
				.ownerId(owner.getUserId())
				.build()
		);

		RestaurantSlot slot = restaurantSlotRepository.save(
			RestaurantSlotFixture.slot()
				.restaurantId(restaurant.getRestaurantId())
				.time(SLOT_TIME)
				.maxCapacity(10)
				.build()
		);

		dailySlotCapacityRepository.save(
			DailySlotCapacityFixture.capacity()
				.slotId(slot.getSlotId())
				.date(VISIT_DATE)
				.remainingCount(10)
				.build()
		);

		ReservationRequestDto req = new ReservationRequestDto(slot.getSlotId(), VISIT_DATE, 2, "concurrency-test");
		reservation = reservationService.create(customer.getEmail(), req);
	}

	@AfterEach
	void tearDown() {
		reservationRepository.deleteAll();
		dailySlotCapacityRepository.deleteAll();
		restaurantSlotRepository.deleteAll();
		restaurantRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	@DisplayName("동시 취소 - @Version 충돌 시 재시도 후 ALREADY_CANCELED, 최종 1건만 성공")
	void cancel_concurrent_doubleCancel_onlyOneSucceeds() throws InterruptedException {
		String email = customer.getEmail();
		Long reservationId = reservation.getReservationId();

		CountDownLatch startLatch = new CountDownLatch(1);
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger alreadyCanceledCount = new AtomicInteger(0);

		Runnable cancelTask = () -> {
			try {
				startLatch.await();
				cancelFacade.cancelWithRetry(email, reservationId);
				successCount.incrementAndGet();
			} catch (ReservationException e) {
				if (e.getErrorCode() == ErrorCode.RESERVATION_ALREADY_CANCELED) {
					alreadyCanceledCount.incrementAndGet();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		};

		Thread t1 = new Thread(cancelTask);
		Thread t2 = new Thread(cancelTask);
		t1.start();
		t2.start();
		startLatch.countDown();
		t1.join();
		t2.join();

		assertThat(successCount.get()).isEqualTo(1);
		assertThat(alreadyCanceledCount.get()).isEqualTo(1);
	}
}
